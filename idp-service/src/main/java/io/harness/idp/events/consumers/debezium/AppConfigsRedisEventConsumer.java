/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.debezium.redisconsumer.DebeziumAbstractRedisConsumer;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.ff.FeatureFlagService;
import io.harness.idp.config.CdcKafkaConfig;
import io.harness.idp.config.CdcKafkaConsumerConfig;
import io.harness.idp.events.consumers.IdpRedisConsumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class AppConfigsRedisEventConsumer extends DebeziumAbstractRedisConsumer implements IdpRedisConsumer {
  private final FeatureFlagService featureFlagService;
  private final boolean redisShortCircuit;

  @Inject
  public AppConfigsRedisEventConsumer(@Named(IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER) Consumer redisConsumer,
      QueueController queueController, AppConfigsChangeEventHandler eventHandler,
      @Named("debeziumEventsCache") Cache<String, Long> eventsCache, FeatureFlagService featureFlagService,
      CdcKafkaConfig cdcKafkaConfig) {
    super(redisConsumer, queueController, eventHandler, eventsCache);
    this.featureFlagService = featureFlagService;
    this.redisShortCircuit = isRedisShortCircuit(cdcKafkaConfig);
  }

  private static boolean isRedisShortCircuit(CdcKafkaConfig cdcKafkaConfig) {
    if (cdcKafkaConfig == null) {
      return false;
    }
    return cdcKafkaConfig.getConsumer(CdcKafkaConfig.APP_CONFIGS_CONSUMER)
        .map(CdcKafkaConsumerConfig::isRedisShortCircuit)
        .orElse(false);
  }

  // Short-circuits Redis processing only when BOTH the FF is ON AND redisShortCircuit=true.
  // During FF transitions both consumers may briefly process the same event — handlers must be idempotent.
  @Override
  protected boolean processMessage(Message message) {
    try {
      if (redisShortCircuit && featureFlagService.isGlobalEnabled(FeatureName.IDP_CDC_KAFKA_APP_CONFIGS)) {
        return true;
      }
    } catch (Exception e) {
      log.warn("Failed to evaluate FF {}, falling back to Redis processing",
          FeatureName.IDP_CDC_KAFKA_APP_CONFIGS.name(), e);
    }
    return super.processMessage(message);
  }
}
