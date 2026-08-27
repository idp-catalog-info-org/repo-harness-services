/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.INITIATE_NODE_BATCH_EVENT_CONSUMER;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.INITIATE_NODE_BATCH_EVENT_PRODUCER;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.INITIATE_NODE_EVENT_CONSUMER;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.INITIATE_NODE_EVENT_PRODUCER;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.SDK_RESPONSE_EVENT_CONSUMER;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.SDK_RESPONSE_SPAWN_EVENT_CONSUMER;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.SDK_STEP_RESPONSE_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.INITIATE_NODE_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.INITIATE_NODE_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.SDK_RESPONSE_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.SDK_RESPONSE_SPAWN_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.SDK_STEP_RESPONSE_EVENT_BATCH_SIZE;
import static io.harness.pms.events.PmsEventFrameworkConstants.MAX_PROCESSING_TIME_SECONDS;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkRedisTopicResolver;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.time.Duration;
import java.util.Optional;
import org.redisson.api.RedissonClient;

public class OrchestrationEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;

  public OrchestrationEventsFrameworkModule(EventsFrameworkConfiguration eventsFrameworkConfiguration) {
    this.eventsFrameworkConfiguration = eventsFrameworkConfiguration;
  }

  // todo: ideally this should come from config, but the structure is complex and we need this asap
  private final Integer initiateNodeTopicSize = Optional.ofNullable(System.getenv("INITIATE_NODE_TOPIC_SIZE"))
                                                    .map(Integer::valueOf)
                                                    .orElse(INITIATE_NODE_EVENT_MAX_TOPIC_SIZE);

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_SPAWN_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_STEP_RESPONSE_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));

      bind(Producer.class)
          .annotatedWith(Names.named(INITIATE_NODE_BATCH_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ORCHESTRATION_LOG))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      String redisConsumerGroupId =
          EventsFrameworkRedisTopicResolver.getPipelineRedisConsumerGroupId(PIPELINE_SERVICE.getServiceId());

      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(EventsFrameworkRedisTopicResolver.getPipelineSdkResponseTopic(),
              redisConsumerGroupId, redissonClient, Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS),
              SDK_RESPONSE_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_SPAWN_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(EventsFrameworkRedisTopicResolver.getPipelineSdkResponseSpawnTopic(),
              redisConsumerGroupId, redissonClient, Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS),
              SDK_RESPONSE_SPAWN_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_STEP_RESPONSE_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(EventsFrameworkRedisTopicResolver.getPipelineSdkStepResponseTopic(),
              redisConsumerGroupId, redissonClient, Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS),
              SDK_STEP_RESPONSE_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));

      // Trigger Node Consumer and producer
      bind(Producer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_PRODUCER))
          .toInstance(RedisProducer.of(EventsFrameworkRedisTopicResolver.getInitiateNodeTopic(), redissonClient,
              initiateNodeTopicSize, PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));

      bind(Producer.class)
          .annotatedWith(Names.named(INITIATE_NODE_BATCH_EVENT_PRODUCER))
          .toInstance(RedisProducer.of(EventsFrameworkRedisTopicResolver.getInitiateNodeBatchTopic(), redissonClient,
              initiateNodeTopicSize, PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(EventsFrameworkRedisTopicResolver.getInitiateNodeTopic(), redisConsumerGroupId,
              redissonClient, Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), INITIATE_NODE_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(INITIATE_NODE_BATCH_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(EventsFrameworkRedisTopicResolver.getInitiateNodeBatchTopic(),
              redisConsumerGroupId, redissonClient, Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS),
              INITIATE_NODE_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ORCHESTRATION_LOG))
          .toInstance(RedisProducer.of(EventsFrameworkRedisTopicResolver.getOrchestrationLogTopic(), redissonClient,
              ORCHESTRATION_LOG_MAX_TOPIC_SIZE, PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
    }
  }
}
