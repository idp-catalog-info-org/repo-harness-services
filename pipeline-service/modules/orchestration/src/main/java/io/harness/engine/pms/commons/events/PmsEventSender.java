/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.commons.events;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_REDIS_URL;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_BACKFILL_ORCHESTRATION_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_TOPIC_WITH_SERVICE_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_INTERRUPT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_INTERRUPT_TOPIC_WITH_SERVICE_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_ADVISE_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_EVENT_TOPIC_WITH_SERVICE_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_TOPIC_WITH_SERVICE_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_EVENT_TOPIC_WITH_SERVICE_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC;
import static io.harness.pms.contracts.plan.ConsumerConfig.ConsumerPreference.KAFKA;
import static io.harness.pms.contracts.plan.ConsumerConfig.ConsumerPreference.REDIS;
import static io.harness.pms.events.PmsEventFrameworkConstants.PIE_EVENT_ID;
import static io.harness.pms.events.PmsEventFrameworkConstants.PIPELINE_MONITORING_ENABLED;
import static io.harness.pms.events.PmsEventFrameworkConstants.SERVICE_NAME;
import static io.harness.pms.events.base.PmsEventCategory.BACKFILL_ORCHESTRATION_EVENT;
import static io.harness.pms.events.base.PmsEventCategory.CREATE_PARTIAL_PLAN;
import static io.harness.pms.events.base.PmsEventCategory.ORCHESTRATION_EVENT;
import static io.harness.steps.StepSpecTypeConstants.INIT_CONTAINER_V2_STEP_TYPE;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.config.OrchestrationModuleConfig;
import io.harness.config.OrchestrationRedisEventsConfig;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.eventsframework.EventsFrameworkRedisTopicResolver;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisProducerFactory;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.ConsumerConfig.ConfigCase;
import io.harness.pms.contracts.plan.Redis;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstance.PmsSdkInstanceKeys;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.RetryUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PmsEventSender {
  private static final RetryPolicy<Object> retryPolicy = RetryUtils.getRetryPolicy("Error Getting Producer..Retrying",
      "Failed to obtain producer", Collections.singletonList(ExecutionException.class), Duration.ofMillis(10), 3, log);
  private static final List<PmsEventCategory> NOT_YET_SUPPORTED_ON_KAFKA =
      List.of(ORCHESTRATION_EVENT, CREATE_PARTIAL_PLAN, BACKFILL_ORCHESTRATION_EVENT);
  @Inject private MongoTemplate mongoTemplate;
  @Inject private OrchestrationModuleConfig moduleConfig;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private RedisProducerFactory redisProducerFactory;
  @Inject @KafkaModule.General private Optional<HKafkaProtoProducer> hKafkaProtoProducer;
  @Inject @Named("skipSdkMongoRegistration") private boolean skipSdkMongoRegistration;

  private final LoadingCache<ProducerCacheKey, Producer> producerCache =
      CacheBuilder.newBuilder()
          .maximumSize(100)
          .expireAfterAccess(300, TimeUnit.MINUTES)
          .build(new CacheLoader<ProducerCacheKey, Producer>() {
            @Override
            public Producer load(@NotNull ProducerCacheKey cacheKey) {
              return obtainProducer(cacheKey);
            }
          });

  private final LoadingCache<ProducerCacheKey, String> kafkaProducerCache =
      // expireAfterAccess will make it never be refreshed in this case, we should revisit it
      CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(300, TimeUnit.MINUTES).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull ProducerCacheKey cacheKey) {
          return obtainKafkaProducerTopic(cacheKey);
        }
      });

  private final LoadingCache<ProducerCacheKey, ConsumerConfig.ConsumerPreference> consumerPreferenceCache =
      CacheBuilder.newBuilder().maximumSize(100).expireAfterAccess(300, TimeUnit.MINUTES).build(new CacheLoader<>() {
        @NotNull
        @Override
        public ConsumerConfig.ConsumerPreference load(@NotNull ProducerCacheKey cacheKey) {
          return resolveConsumerPreference(cacheKey.getEventCategory(), cacheKey.getServiceName());
        }
      });

  public void sendEvent(Ambiance ambiance, com.google.protobuf.Message message, PmsEventCategory eventCategory,
      String serviceName, boolean isMonitored, boolean useDeterministicEventId) {
    var resolvedServiceName = extractServiceName(ambiance, serviceName);
    String eventId = getEventId(ambiance, eventCategory, useDeterministicEventId);
    ImmutableMap.Builder<String, String> metadataBuilder = ImmutableMap.<String, String>builder()
                                                               .put(SERVICE_NAME, resolvedServiceName)
                                                               .put(PIE_EVENT_ID, eventId)
                                                               .putAll(AmbianceUtils.logContextMap(ambiance));
    if (isMonitored) {
      metadataBuilder.put(PIPELINE_MONITORING_ENABLED, "true");
    }

    ConsumerConfig.ConsumerPreference consumerPreference = obtainConsumerPreference(eventCategory, resolvedServiceName);
    routeMessageAndSend(
        message, metadataBuilder.build(), consumerPreference, eventCategory, resolvedServiceName, ambiance);
  }

  @VisibleForTesting
  ConsumerConfig.ConsumerPreference obtainConsumerPreference(
      PmsEventCategory eventCategory, String resolvedServiceName) {
    ProducerCacheKey cacheKey =
        ProducerCacheKey.builder().eventCategory(eventCategory).serviceName(resolvedServiceName).build();
    ConsumerConfig.ConsumerPreference consumerPreference =
        Failsafe.with(retryPolicy).get(() -> consumerPreferenceCache.get(cacheKey));
    if (consumerPreference == null) {
      throw new RuntimeException("Cannot get Event Framework consumer preference");
    }
    return consumerPreference;
  }

  @VisibleForTesting
  ConsumerConfig.ConsumerPreference resolveConsumerPreference(
      PmsEventCategory eventCategory, String resolvedServiceName) {
    if (!moduleConfig.getEventsFrameworkConfiguration().isShouldUseKafka()) {
      return REDIS;
    }
    if (skipSdkMongoRegistration) {
      return KAFKA;
    }
    try {
      PmsSdkInstance instance = getPmsSdkInstance(resolvedServiceName);
      ConsumerConfig consumerConfig = getConsumerConfigForCategory(instance, eventCategory);
      if (consumerConfig.getConsumerPreference() == REDIS
          || isCustomRedisStream(eventCategory, resolvedServiceName, consumerConfig)) {
        return REDIS;
      }
    } catch (InvalidRequestException ex) {
      log.debug("SDK not registered for service {}, using global Kafka preference", resolvedServiceName);
    }
    return KAFKA;
  }

  private ConsumerConfig getConsumerConfigForCategory(PmsSdkInstance instance, PmsEventCategory eventCategory) {
    return switch (eventCategory) {
      case INTERRUPT_EVENT -> instance.getInterruptConsumerConfig();
      case ORCHESTRATION_EVENT -> instance.getOrchestrationEventConsumerConfig();
      case FACILITATOR_EVENT -> instance.getFacilitatorEventConsumerConfig();
      case NODE_START -> instance.getNodeStartEventConsumerConfig();
      case PROGRESS_EVENT -> instance.getProgressEventConsumerConfig();
      case NODE_ADVISE -> instance.getNodeAdviseEventConsumerConfig();
      case NODE_RESUME -> instance.getNodeResumeEventConsumerConfig();
      case CREATE_PARTIAL_PLAN -> instance.getStartPlanCreationEventConsumerConfig();
      case BACKFILL_ORCHESTRATION_EVENT -> instance.getBackfillOrchestrationEventConsumerConfig();
      default -> throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    };
  }

  /**
   * Custom SDK streams (e.g. RMG {@code rmgstart}) register non-standard Redis topics. Those services consume from
   * Redis only, so pipeline-service must not route their events to Kafka even when global Kafka is enabled.
   */
  @VisibleForTesting
  boolean isCustomRedisStream(
      PmsEventCategory eventCategory, String serviceName, ConsumerConfig consumerConfig) {
    Redis redis = getRedisSettings(consumerConfig);
    if (redis == null || isEmpty(redis.getTopicName())) {
      return false;
    }
    ProducerCacheKey cacheKey =
        ProducerCacheKey.builder().eventCategory(eventCategory).serviceName(serviceName).build();
    String effectiveRedisTopic = resolveRedisProducerTopicName(redis, cacheKey);
    return !effectiveRedisTopic.equals(resolveRedisProducerTopic(eventCategory, serviceName));
  }

  private Redis getRedisSettings(ConsumerConfig consumerConfig) {
    if (consumerConfig.hasRedisSettings()) {
      return consumerConfig.getRedisSettings();
    }
    if (consumerConfig.hasRedis()) {
      return consumerConfig.getRedis();
    }
    return null;
  }

  private void routeMessageAndSend(com.google.protobuf.Message message, Map<String, String> metadata,
      ConsumerConfig.ConsumerPreference consumerPreference, PmsEventCategory eventCategory, String resolvedServiceName,
      Ambiance ambiance) {
    // we can split by pmsEventCategory, but doesn't feel right
    var isKafkaEnabled = pmsFeatureFlagService.isEnabled(
        AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING);
    if (!NOT_YET_SUPPORTED_ON_KAFKA.contains(eventCategory) && KAFKA.equals(consumerPreference) && isKafkaEnabled) {
      if (hKafkaProtoProducer.isPresent()) {
        sendKafkaEvent(message, metadata, eventCategory, resolvedServiceName);
        return;
      }
      log.warn("Kafka producer is not present, check the configuration. Fallback to redis.");
    }
    // this is the default behaviour until rollout works properly
    long startTs = System.currentTimeMillis();
    Producer producer = obtainProducer(eventCategory, resolvedServiceName);
    String messageId =
        producer.send(Message.newBuilder().putAllMetadata(metadata).setData(message.toByteString()).build());
    log.info("Successfully Sent {} event for {} to the producer. MessageId {} in [{}ms]", eventCategory,
        resolvedServiceName, messageId, System.currentTimeMillis() - startTs);
  }

  private void sendKafkaEvent(com.google.protobuf.Message message, Map<String, String> metadata,
      PmsEventCategory eventCategory, String resolvedServiceName) {
    var producerTopic = obtainKafkaProducerTopicFromCache(eventCategory, resolvedServiceName);
    hKafkaProtoProducer.get().send(producerTopic, message, metadata);
  }

  private String extractServiceName(Ambiance ambiance, String serviceName) {
    StepType stepType = AmbianceUtils.getCurrentStepType(ambiance);
    if (stepType != null && INIT_CONTAINER_V2_STEP_TYPE.getType().equals(stepType.getType())) {
      serviceName = ModuleType.PMS.name().toLowerCase();
    }
    // NodeTypeLookupServiceImpl we handled FF stage module to be set as cf service though its pms
    if (serviceName.equals("cf")) {
      serviceName = ModuleType.PMS.name().toLowerCase();
    }
    return serviceName;
  }

  private String getEventId(Ambiance ambiance, PmsEventCategory eventCategory, boolean useDeterministicEventId) {
    if (useDeterministicEventId) {
      return AmbianceUtils.getEventId(eventCategory.name(), AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    } else {
      return generateUuid();
    }
  }

  @VisibleForTesting
  String obtainKafkaProducerTopicFromCache(PmsEventCategory eventCategory, String serviceName) {
    var topic =
        Failsafe.with(retryPolicy)
            .get(()
                     -> kafkaProducerCache.get(
                         ProducerCacheKey.builder().eventCategory(eventCategory).serviceName(serviceName).build()));
    if (topic == null) {
      throw new RuntimeException("Cannot create Event Framework producer");
    }
    return topic;
  }

  @VisibleForTesting
  String obtainKafkaProducerTopic(ProducerCacheKey cacheKey) {
    return switch (cacheKey.getEventCategory()) {
      case INTERRUPT_EVENT -> EventsFrameworkKafkaTopicResolver.getInterruptTopic(cacheKey.getServiceName());
      case ORCHESTRATION_EVENT -> EventsFrameworkKafkaTopicResolver.getOrchestrationTopic(cacheKey.getServiceName());
      case FACILITATOR_EVENT -> EventsFrameworkKafkaTopicResolver.getFacilitationTopic(cacheKey.getServiceName());
      case NODE_START -> EventsFrameworkKafkaTopicResolver.getNodeStartTopic(cacheKey.getServiceName());
      case PROGRESS_EVENT -> EventsFrameworkKafkaTopicResolver.getProgressTopic(cacheKey.getServiceName());
      case NODE_ADVISE -> EventsFrameworkKafkaTopicResolver.getNodeAdviseTopic(cacheKey.getServiceName());
      case NODE_RESUME -> EventsFrameworkKafkaTopicResolver.getNodeResumeTopic(cacheKey.getServiceName());
      default -> throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    };
  }

  @VisibleForTesting
  Producer obtainProducer(PmsEventCategory eventCategory, String serviceName) {
    Producer producer =
        Failsafe.with(retryPolicy)
            .get(()
                     -> producerCache.get(
                         ProducerCacheKey.builder().eventCategory(eventCategory).serviceName(serviceName).build()));
    if (producer == null) {
      throw new RuntimeException("Cannot create Event Framework producer");
    }
    return producer;
  }

  @VisibleForTesting
  Producer obtainProducer(ProducerCacheKey cacheKey) {
    if (skipSdkMongoRegistration) {
      return buildRedisProducer(resolveRedisProducerTopic(cacheKey.getEventCategory(), cacheKey.getServiceName()),
          PIPELINE_SERVICE.getServiceId(), getMaxTopicSizeForCategory(cacheKey.getEventCategory()));
    }
    PmsSdkInstance instance = getPmsSdkInstance(cacheKey.getServiceName());
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig = moduleConfig.getOrchestrationRedisEventsConfig();
    switch (cacheKey.getEventCategory()) {
      case INTERRUPT_EVENT:
        return extractProducer(instance.getInterruptConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineInterruptEvent().getMaxTopicSize(), cacheKey);
      case ORCHESTRATION_EVENT:
        return extractProducer(instance.getOrchestrationEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineOrchestrationEvent().getMaxTopicSize(), cacheKey);
      case FACILITATOR_EVENT:
        return extractProducer(instance.getFacilitatorEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineFacilitatorEvent().getMaxTopicSize(), cacheKey);
      case NODE_START:
        return extractProducer(instance.getNodeStartEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineNodeStartEvent().getMaxTopicSize(), cacheKey);
      case PROGRESS_EVENT:
        return extractProducer(instance.getProgressEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineProgressEvent().getMaxTopicSize(), cacheKey);
      case NODE_ADVISE:
        return extractProducer(instance.getNodeAdviseEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineNodeAdviseEvent().getMaxTopicSize(), cacheKey);
      case NODE_RESUME:
        return extractProducer(instance.getNodeResumeEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineNodeResumeEvent().getMaxTopicSize(), cacheKey);
      case CREATE_PARTIAL_PLAN:
        return extractProducer(instance.getStartPlanCreationEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineStartPartialPlanCreator().getMaxTopicSize(), cacheKey);
      case BACKFILL_ORCHESTRATION_EVENT:
        return extractProducer(instance.getBackfillOrchestrationEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineBackfillOrchestrationEvent().getMaxTopicSize(), cacheKey);
      default:
        throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    }
  }

  private Producer extractProducer(ConsumerConfig consumerConfig, int topicSize, ProducerCacheKey cacheKey) {
    ConfigCase configCase = consumerConfig.getConfigCase();
    switch (configCase) {
      case REDIS:
        return buildRedisProducer(resolveRedisProducerTopicName(consumerConfig.getRedis(), cacheKey),
            PIPELINE_SERVICE.getServiceId(), topicSize);
      case CONFIG_NOT_SET:
      default:
        throw new InvalidRequestException("No producer found for Config Case " + configCase.name());
    }
  }

  /**
   * Prefer Mongo-registered topic names for custom SDK streams (e.g. RMG {@code rmgstart}). When Mongo holds a
   * standard default topic polluted by another devspace's SDK registration (exact default or default with suffix),
   * use the resolver so producer and consumer wiring stay aligned.
   */
  @VisibleForTesting
  String resolveRedisProducerTopicName(Redis redis, ProducerCacheKey cacheKey) {
    String resolverTopic = resolveRedisProducerTopic(cacheKey.getEventCategory(), cacheKey.getServiceName());
    if (redis == null || isEmpty(redis.getTopicName())) {
      return resolverTopic;
    }
    String mongoTopic = redis.getTopicName();
    if (mongoTopic.equals(resolverTopic)) {
      return mongoTopic;
    }
    String defaultTopic = getDefaultRedisTopicConstant(cacheKey.getEventCategory(), cacheKey.getServiceName());
    String flatDefaultTopic = getFlatDefaultRedisTopicConstant(cacheKey.getEventCategory());
    String envNamespace = "";
    EventsFrameworkConfiguration eventsFrameworkConfiguration = moduleConfig.getEventsFrameworkConfiguration();
    if (eventsFrameworkConfiguration != null && eventsFrameworkConfiguration.getRedisConfig() != null) {
      envNamespace = eventsFrameworkConfiguration.getRedisConfig().getEnvNamespace();
    }
    if (isNotEmpty(envNamespace)) {
      if (mongoTopic.equals(defaultTopic) || mongoTopic.startsWith(defaultTopic + "_")) {
        return resolverTopic;
      }
      if (moduleConfig.isStreamPerServiceConfiguration() && mongoTopic.equals(flatDefaultTopic)) {
        return resolverTopic;
      }
      if (!moduleConfig.isStreamPerServiceConfiguration()
          && (mongoTopic.equals(flatDefaultTopic) || mongoTopic.startsWith(flatDefaultTopic + "_"))) {
        return resolverTopic;
      }
    }
    return mongoTopic;
  }

  private String getFlatDefaultRedisTopicConstant(PmsEventCategory eventCategory) {
    return switch (eventCategory) {
      case INTERRUPT_EVENT -> PIPELINE_INTERRUPT_TOPIC;
      case ORCHESTRATION_EVENT -> PIPELINE_ORCHESTRATION_EVENT_TOPIC;
      case FACILITATOR_EVENT -> PIPELINE_FACILITATOR_EVENT_TOPIC;
      case NODE_START -> PIPELINE_NODE_START_EVENT_TOPIC;
      case PROGRESS_EVENT -> PIPELINE_PROGRESS_EVENT_TOPIC;
      case NODE_ADVISE -> PIPELINE_NODE_ADVISE_EVENT_TOPIC;
      case NODE_RESUME -> PIPELINE_NODE_RESUME_EVENT_TOPIC;
      case CREATE_PARTIAL_PLAN -> START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC;
      case BACKFILL_ORCHESTRATION_EVENT -> PIPELINE_BACKFILL_ORCHESTRATION_EVENT_TOPIC;
      default -> throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    };
  }

  private int getMaxTopicSizeForCategory(PmsEventCategory eventCategory) {
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig = moduleConfig.getOrchestrationRedisEventsConfig();
    return switch (eventCategory) {
      case INTERRUPT_EVENT -> orchestrationRedisEventsConfig.getPipelineInterruptEvent().getMaxTopicSize();
      case ORCHESTRATION_EVENT -> orchestrationRedisEventsConfig.getPipelineOrchestrationEvent().getMaxTopicSize();
      case FACILITATOR_EVENT -> orchestrationRedisEventsConfig.getPipelineFacilitatorEvent().getMaxTopicSize();
      case NODE_START -> orchestrationRedisEventsConfig.getPipelineNodeStartEvent().getMaxTopicSize();
      case PROGRESS_EVENT -> orchestrationRedisEventsConfig.getPipelineProgressEvent().getMaxTopicSize();
      case NODE_ADVISE -> orchestrationRedisEventsConfig.getPipelineNodeAdviseEvent().getMaxTopicSize();
      case NODE_RESUME -> orchestrationRedisEventsConfig.getPipelineNodeResumeEvent().getMaxTopicSize();
      case CREATE_PARTIAL_PLAN -> orchestrationRedisEventsConfig.getPipelineStartPartialPlanCreator().getMaxTopicSize();
      case BACKFILL_ORCHESTRATION_EVENT
          -> orchestrationRedisEventsConfig.getPipelineBackfillOrchestrationEvent().getMaxTopicSize();
      default -> throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    };
  }

  private String getDefaultRedisTopicConstant(PmsEventCategory eventCategory, String serviceName) {
    boolean streamPerServiceConfiguration = moduleConfig.isStreamPerServiceConfiguration();
    return switch (eventCategory) {
      case INTERRUPT_EVENT
          -> streamPerServiceConfiguration ? String.format(PIPELINE_INTERRUPT_TOPIC_WITH_SERVICE_NAME, serviceName):
        PIPELINE_INTERRUPT_TOPIC;
      case ORCHESTRATION_EVENT -> PIPELINE_ORCHESTRATION_EVENT_TOPIC;
          case FACILITATOR_EVENT
          -> streamPerServiceConfiguration
              ? String.format(PIPELINE_FACILITATOR_EVENT_TOPIC_WITH_SERVICE_NAME, serviceName)
              :
        PIPELINE_FACILITATOR_EVENT_TOPIC;
      case NODE_START
          -> streamPerServiceConfiguration
          ? String.format(PIPELINE_NODE_START_EVENT_TOPIC_WITH_SERVICE_NAME, serviceName)
          :
        PIPELINE_NODE_START_EVENT_TOPIC;
      case PROGRESS_EVENT
          -> streamPerServiceConfiguration ? String.format(PIPELINE_PROGRESS_EVENT_TOPIC_WITH_SERVICE_NAME, serviceName)
                                           :
        PIPELINE_PROGRESS_EVENT_TOPIC;
      case NODE_ADVISE -> PIPELINE_NODE_ADVISE_EVENT_TOPIC;
          case NODE_RESUME
          -> streamPerServiceConfiguration
              ? String.format(PIPELINE_NODE_RESUME_EVENT_TOPIC_WITH_SERVICE_NAME, serviceName)
              :
        PIPELINE_NODE_RESUME_EVENT_TOPIC;
      case CREATE_PARTIAL_PLAN -> START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC;
      case BACKFILL_ORCHESTRATION_EVENT -> PIPELINE_BACKFILL_ORCHESTRATION_EVENT_TOPIC;
      default -> throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    };
  }

  /**
   * Fallback topic resolution aligned with {@link io.harness.pms.sdk.PmsSdkInitHelper#buildConsumerRedisConfig}.
   */
  @VisibleForTesting
  String resolveRedisProducerTopic(PmsEventCategory eventCategory, String serviceName) {
    boolean streamPerServiceConfiguration = moduleConfig.isStreamPerServiceConfiguration();
    return switch (eventCategory) {
      case INTERRUPT_EVENT
          -> streamPerServiceConfiguration
          ? EventsFrameworkRedisTopicResolver.getPipelineInterruptServiceTopic(serviceName)
          :
        EventsFrameworkRedisTopicResolver.getPipelineInterruptTopic();
      case ORCHESTRATION_EVENT -> EventsFrameworkRedisTopicResolver.getPipelineOrchestrationTopic();
          case FACILITATOR_EVENT
          -> streamPerServiceConfiguration
              ? EventsFrameworkRedisTopicResolver.getPipelineNodeFacilitationServiceTopic(serviceName)
              :
        EventsFrameworkRedisTopicResolver.getPipelineNodeFacilitationTopic();
      case NODE_START
          -> streamPerServiceConfiguration
          ? EventsFrameworkRedisTopicResolver.getPipelineNodeStartServiceTopic(serviceName)
          :
        EventsFrameworkRedisTopicResolver.getPipelineNodeStartTopic();
      case PROGRESS_EVENT
          -> streamPerServiceConfiguration
          ? EventsFrameworkRedisTopicResolver.getPipelineNodeProgressServiceTopic(serviceName)
          :
        EventsFrameworkRedisTopicResolver.getPipelineNodeProgressTopic();
      case NODE_ADVISE -> EventsFrameworkRedisTopicResolver.getPipelineNodeAdviseTopic();
          case NODE_RESUME
          -> streamPerServiceConfiguration
              ? EventsFrameworkRedisTopicResolver.getPipelineNodeResumeServiceTopic(serviceName)
              :
        EventsFrameworkRedisTopicResolver.getPipelineNodeResumeTopic();
      case CREATE_PARTIAL_PLAN -> EventsFrameworkRedisTopicResolver.getPipelineStartPartialPlanTopic();
      case BACKFILL_ORCHESTRATION_EVENT -> EventsFrameworkRedisTopicResolver.getPipelineBackfillOrchestrationTopic();
      default -> throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    };
  }

  private Producer buildRedisProducer(String topicName, String serviceId, int topicSize) {
    RedisConfig redisConfig = moduleConfig.getEventsFrameworkConfiguration().getRedisConfig();
    return redisConfig.getRedisUrl().equals(DUMMY_REDIS_URL)
        ? NoOpProducer.of(topicName)
        :
        redisProducerFactory.createRedisProducer(topicName, RedissonClientFactory.getClient(redisConfig), topicSize,
            serviceId, redisConfig.getEnvNamespace());
    }

    @VisibleForTesting
    PmsSdkInstance getPmsSdkInstance(String serviceName) {
      Query query = query(where(PmsSdkInstanceKeys.name).is(serviceName));
      PmsSdkInstance instance = mongoTemplate.findOne(query, PmsSdkInstance.class);
      if (instance == null) {
        throw new InvalidRequestException("Sdk Not registered for Service name" + serviceName);
      }
      return instance;
    }
  }
