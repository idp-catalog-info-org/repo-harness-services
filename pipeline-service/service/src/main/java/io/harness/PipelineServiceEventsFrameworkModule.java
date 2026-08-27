/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_GROUP_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_TOPIC_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.PLAN_NOTIFY_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.SYSTEM_EVENT_TRIGGER_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_REQUEST_PAYLOAD_DETAILS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cache.HarnessCacheManager;
import io.harness.config.PipelineRedisEventsConfig;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkRedisTopicResolver;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.GitAwareRedisProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.pms.event.overviewLandingPage.DebeziumConsumersConfig;
import io.harness.pms.event.overviewLandingPage.kafka.CdcKafkaConstants;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.version.VersionInfoManager;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor
@OwnedBy(PIPELINE)
public class PipelineServiceEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final PipelineRedisEventsConfig pipelineRedisEventsConfig;
  private final DebeziumConsumersConfig debeziumConsumersConfigs;
  private final EventsFrameworkConfiguration eventsFrameworkSnapshotConfiguration;
  private final boolean shouldUseEventsFrameworkSnapshotDebezium;

  @Provides
  @Singleton
  @Named("debeziumEventsCache")
  public Cache<String, Long> debeziumEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("debeziumEventsCache", String.class, Long.class,
        AccessedExpiryPolicy.factoryOf(Duration.ONE_HOUR), versionInfoManager.getVersionInfo().getBuildNo());
  }

  /**
   * Executor used by the {@code PipelineExecutionSummaryKafkaConsumer} which runs in
   * {@code UNORDERED + isNoAck=false} mode ({@code runAck} path in {@link HKafkaConsumer}).
   *
   * <p>Single thread (size=1) is intentional: {@code runAck} submits all records from a poll
   * batch as individual {@code CompletableFuture} tasks to this pool. With 1 thread the tasks
   * execute sequentially in submission order, preserving per-partition event ordering and
   * eliminating any risk of concurrent writes for the same document id. The poll-loop thread
   * blocks in {@code allOf.get()} without holding the Kafka consumer monitor, so there is
   * zero lock contention — this is the fix for the ORDERED-mode livelock where the poll
   * thread held the monitor for 1000ms while the executor was trying to call {@code resume()}.
   *
   * <p>Throughput: 1 thread × (1/3ms per record) ≈ 333 records/s per partition, which is
   * sufficient to drain the largest observed surge (175k records) in under 3 minutes.
   */
  @Provides
  @Singleton
  @Named(CdcKafkaConstants.CDC_KAFKA_EXECUTOR_SERVICE)
  public ExecutorService cdcKafkaExecutorService() {
    return Executors.newFixedThreadPool(
        1, new ThreadFactoryBuilder().setNameFormat("cdc-kafka-consumer-%d").setDaemon(true).build());
  }

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_TOPIC));
      bind(Producer.class)
          .annotatedWith(Names.named(WEBHOOK_REQUEST_PAYLOAD_DETAILS))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(SYSTEM_EVENT_TRIGGER_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(SYSTEM_EVENT_TRIGGER_STREAM))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ASYNC_FILTER_CREATION))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ASYNC_FILTER_CREATION))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      String setupUsageTopic = EventsFrameworkRedisTopicResolver.getSetupUsageTopic();
      String planNotifyTopic = EventsFrameworkRedisTopicResolver.getPlanNotifyTopic();
      String webhookRequestPayloadDetailsTopic =
          EventsFrameworkRedisTopicResolver.getWebhookRequestPayloadDetailsTopic();
      String webhookEventsTopic = EventsFrameworkRedisTopicResolver.getWebhookEventsTopic();
      String systemEventTriggerTopic = EventsFrameworkRedisTopicResolver.getSystemEventTriggerTopic();
      String entityCrudTopic = EventsFrameworkRedisTopicResolver.getEntityCrudTopic();
      String orchestrationNotifyTopic = EventsFrameworkRedisTopicResolver.getPmsOrchestrationNotifyTopic();
      String triggerExecutionEventsTopic = EventsFrameworkRedisTopicResolver.getTriggerExecutionEventsTopic();
      String asyncFilterCreationTopic = EventsFrameworkRedisTopicResolver.getAsyncFilterCreationTopic();
      String pollingEventsTopic = EventsFrameworkRedisTopicResolver.getPollingEventsTopic();
      String entityActivityTopic = EventsFrameworkRedisTopicResolver.getEntityActivityTopic();
      String executionRetentionCleanupTopic = EventsFrameworkRedisTopicResolver.getExecutionRetentionCleanupTopic();
      String bulkReconciliationTopic = EventsFrameworkRedisTopicResolver.getBulkReconciliationTopic();

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(GitAwareRedisProducer.of(setupUsageTopic, redissonClient,
              pipelineRedisEventsConfig.getSetupUsage().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_PRODUCER))
          .toInstance(GitAwareRedisProducer.of(planNotifyTopic, redissonClient,
              pipelineRedisEventsConfig.getPlanNotifyEvent().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(WEBHOOK_REQUEST_PAYLOAD_DETAILS))
          .toInstance(RedisProducer.of(webhookRequestPayloadDetailsTopic, redissonClient,
              pipelineRedisEventsConfig.getWebhookPayloadDetails().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM))
          .toInstance(RedisProducer.of(webhookEventsTopic, redissonClient,
              EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_MAX_TOPIC_SIZE, PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(SYSTEM_EVENT_TRIGGER_STREAM))
          .toInstance(RedisProducer.of(systemEventTriggerTopic, redissonClient,
              EventsFrameworkConstants.SYSTEM_EVENT_TRIGGER_STREAM_MAX_TOPIC_SIZE, PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisProducer.of(entityCrudTopic, redissonClient,
              pipelineRedisEventsConfig.getEntityCrud().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT))
          .toInstance(GitAwareRedisProducer.of(orchestrationNotifyTopic, redissonClient,
              pipelineRedisEventsConfig.getOrchestrationNotifyEvent().getMaxTopicSize(),
              PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM))
          .toInstance(RedisProducer.of(triggerExecutionEventsTopic, redissonClient,
              EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM_MAX_TOPIC_SIZE, PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ASYNC_FILTER_CREATION))
          .toInstance(RedisProducer.of(asyncFilterCreationTopic, redissonClient,
              EventsFrameworkConstants.ASYNC_FILTER_CREATION_EVENTS_STREAM_MAX_TOPIC_SIZE,
              PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisConsumer.of(entityCrudTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.POLLING_EVENTS_STREAM))
          .toInstance(RedisConsumer.of(pollingEventsTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.POLLING_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.POLLING_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM))
          .toInstance(RedisConsumer.of(webhookEventsTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.EVENT_LISTENER_STEP_EVENTS_STREAM))
          .toInstance(RedisConsumer.of(webhookEventsTopic, EventsFrameworkConstants.EVENT_LISTENER_STEP_CONSUMER_GROUP,
              redissonClient, EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(SYSTEM_EVENT_TRIGGER_STREAM))
          .toInstance(
              RedisConsumer.of(systemEventTriggerTopic, EventsFrameworkConstants.SYSTEM_EVENT_TRIGGER_CONSUMER_GROUP,
                  redissonClient, EventsFrameworkConstants.SYSTEM_EVENT_TRIGGER_STREAM_MAX_PROCESSING_TIME,
                  EventsFrameworkConstants.SYSTEM_EVENT_TRIGGER_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PLAN_NOTIFY_EVENT_TOPIC))
          .toInstance(RedisConsumer.of(planNotifyTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.PLAN_NOTIFY_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.PLAN_NOTIFY_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PMS_ORCHESTRATION_NOTIFY_EVENT))
          .toInstance(RedisConsumer.of(orchestrationNotifyTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.PLAN_NOTIFY_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfigs.getPlanExecutionsSummaryStreaming().getTopic(),
              PIPELINE_SERVICE.getServiceId(), redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              debeziumConsumersConfigs.getPlanExecutionsSummaryStreaming().getBatchSize(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM))
          .toInstance(RedisConsumer.of(triggerExecutionEventsTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ASYNC_FILTER_CREATION))
          .toInstance(RedisConsumer.of(asyncFilterCreationTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.ASYNC_FILTER_CREATION_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.FILTER_CREATION_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
          .toInstance(RedisProducer.of(entityActivityTopic, redissonClient,
              EventsFrameworkConstants.ENTITY_ACTIVITY_MAX_TOPIC_SIZE, PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT))
          .toInstance(RedisProducer.of(executionRetentionCleanupTopic, redissonClient,
              EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT_MAX_TOPIC_SIZE,
              PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.BULK_RECONCILIATION_EVENT))
          .toInstance(RedisConsumer.of(bulkReconciliationTopic, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.BULK_RECONCILIATION_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.BULK_RECONCILIATION_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      if (shouldUseEventsFrameworkSnapshotDebezium) {
        RedisConfig redisConfigSnapshot = this.eventsFrameworkSnapshotConfiguration.getRedisConfig();
        RedissonClient redissonClientSnapshot = RedissonClientFactory.getClient(redisConfigSnapshot);
        bind(Consumer.class)
            .annotatedWith(Names.named(PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER))
            .toInstance(RedisConsumer.of(debeziumConsumersConfigs.getPlanExecutionsSummarySnapshot().getTopic(),
                PIPELINE_SERVICE.getServiceId(), redissonClientSnapshot,
                EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
                debeziumConsumersConfigs.getPlanExecutionsSummarySnapshot().getBatchSize(),
                redisConfig.getEnvNamespace()));
      } else {
        bind(Consumer.class)
            .annotatedWith(Names.named(PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER))
            .toInstance(new NoOpConsumer(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME));
      }
    }
  }
}
