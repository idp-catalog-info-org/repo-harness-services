/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events;

import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CHECKS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_SCORECARDS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSES_REDIS_EVENT_CONSUMER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.idp.events.config.DebeziumConsumersConfig;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.time.Duration;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor
public class EventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final DebeziumConsumersConfig debeziumConsumersConfig;

  @Provides
  @Singleton
  @Named("debeziumEventsCache")
  public Cache<String, Long> debeziumEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("debeziumEventsCache", String.class, Long.class,
        AccessedExpiryPolicy.factoryOf(javax.cache.expiry.Duration.ONE_HOUR),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("CdcKafkaExecutorService")
  public java.util.concurrent.ExecutorService cdcKafkaExecutorService() {
    return java.util.concurrent.Executors.newFixedThreadPool(6,
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setNameFormat("cdc-kafka-consumer-%d")
            .setDaemon(true)
            .build());
  }

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if ("dummyRedisUrl".equals(redisConfig.getRedisUrl())) {
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_API_ENDPOINT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PROJECT_MOVEMENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PROJECT_TOPOLOGY_REBUILD))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_SCORECARDS_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(MODULE_LICENSES_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_CHECKS_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_CATALOG_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PROJECT_EVENTS_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.STO_SCAN_COMPLETION_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_INTEGRATION_CRUD_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, IDP_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.USERMEMBERSHIP, IDP_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT,
              IDP_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT,
              IDP_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC, redissonClient,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_MAX_PROCESSING_TIME_V3,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_BATCH_SIZE_V3,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT, redissonClient,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_MAX_PROCESSING_TIME_V3,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_BATCH_SIZE_V3,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_API_ENDPOINT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_API_ENDPOINT, redissonClient,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_MAX_PROCESSING_TIME_V3,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_BATCH_SIZE_V3,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PROJECT_MOVEMENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.PROJECT_EVENTS_STREAM,
              EventsFrameworkConstants.PROJECT_MOVEMENT, redissonClient,
              EventsFrameworkConstants.PROJECT_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.PROJECT_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PROJECT_TOPOLOGY_REBUILD))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.PROJECT_EVENTS_STREAM,
              EventsFrameworkConstants.PROJECT_TOPOLOGY_REBUILD, redissonClient,
              EventsFrameworkConstants.PROJECT_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.PROJECT_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT,
              IDP_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_CAPTURE_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_CAPTURE_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfig.getBackstageCatalog().getTopic(),
              IDP_SERVICE.getServiceId(), redissonClient,
              Duration.ofSeconds(debeziumConsumersConfig.getBackstageCatalog().getMaxProcessingTimeSeconds()),
              debeziumConsumersConfig.getBackstageCatalog().getBatchSize(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfig.getBackstageScaffolderTasks().getTopic(),
              IDP_SERVICE.getServiceId(), redissonClient,
              Duration.ofSeconds(debeziumConsumersConfig.getBackstageScaffolderTasks().getMaxProcessingTimeSeconds()),
              debeziumConsumersConfig.getBackstageScaffolderTasks().getBatchSize(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_SCORECARDS_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfig.getScorecards().getTopic(), IDP_SERVICE.getServiceId(),
              redissonClient, Duration.ofSeconds(debeziumConsumersConfig.getScorecards().getMaxProcessingTimeSeconds()),
              debeziumConsumersConfig.getScorecards().getBatchSize(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfig.getAppConfigs().getTopic(), IDP_SERVICE.getServiceId(),
              redissonClient, Duration.ofSeconds(debeziumConsumersConfig.getAppConfigs().getMaxProcessingTimeSeconds()),
              debeziumConsumersConfig.getAppConfigs().getBatchSize(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(MODULE_LICENSES_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfig.getModuleLicenses().getTopic(),
              IDP_SERVICE.getServiceId(), redissonClient,
              Duration.ofSeconds(debeziumConsumersConfig.getModuleLicenses().getMaxProcessingTimeSeconds()),
              debeziumConsumersConfig.getModuleLicenses().getBatchSize(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_CATALOG_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfig.getCatalog().getTopic(), IDP_SERVICE.getServiceId(),
              redissonClient, Duration.ofSeconds(debeziumConsumersConfig.getCatalog().getMaxProcessingTimeSeconds()),
              debeziumConsumersConfig.getCatalog().getBatchSize(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(IDP_CHECKS_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfig.getChecks().getTopic(), IDP_SERVICE.getServiceId(),
              redissonClient, Duration.ofSeconds(debeziumConsumersConfig.getChecks().getMaxProcessingTimeSeconds()),
              debeziumConsumersConfig.getChecks().getBatchSize(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.SETUP_USAGE, redissonClient,
              EventsFrameworkConstants.SETUP_USAGE_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.ENTITY_CRUD, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.USERMEMBERSHIP, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT, redissonClient,
              EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT_MAX_TOPIC_SIZE,
              IDP_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT, redissonClient,
              EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_MAX_TOPIC_SIZE,
              IDP_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3,
              redissonClient, EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_MAX_TOPIC_SIZE_V3,
              IDP_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PROJECT_EVENTS_STREAM))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.PROJECT_EVENTS_STREAM, redissonClient,
              EventsFrameworkConstants.PROJECT_EVENTS_STREAM_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT,
              redissonClient, EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_CAPTURE_EVENT_MAX_TOPIC_SIZE,
              IDP_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.STO_SCAN_COMPLETION_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.STO_SCAN_STREAM, IDP_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.STO_SCAN_COMPLETION_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_INTEGRATION_CRUD_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_INTEGRATION_CRUD_EVENT, IDP_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.IDP_INTEGRATION_CRUD_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_INTEGRATION_CRUD_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT,
              IDP_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT, redissonClient,
              EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT_MAX_TOPIC_SIZE,
              IDP_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT,
              IDP_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT, redissonClient,
              EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT, IDP_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT, redissonClient,
              EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT, IDP_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT, redissonClient,
              EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
    }
  }
}
