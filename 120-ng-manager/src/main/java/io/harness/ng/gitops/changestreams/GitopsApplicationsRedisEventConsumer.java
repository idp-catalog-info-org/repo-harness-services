/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.eventsframework.EventsFrameworkConstants.GITOPS_APPLICATIONS_REDIS_EVENT_CONSUMER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.debezium.redisconsumer.DebeziumAbstractRedisConsumer;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(GITOPS)
@Singleton
public class GitopsApplicationsRedisEventConsumer extends DebeziumAbstractRedisConsumer {
  private final CdcKafkaConfig cdcKafkaConfig;

  @Inject
  public GitopsApplicationsRedisEventConsumer(@Named(GITOPS_APPLICATIONS_REDIS_EVENT_CONSUMER) Consumer redisConsumer,
      QueueController queueController, GitopsApplicationsRedisEventHandler eventHandler,
      @Named("debeziumEventsCache") Cache<String, Long> eventsCache, CdcKafkaConfig cdcKafkaConfig) {
    super(redisConsumer, queueController, eventHandler, eventsCache);
    this.cdcKafkaConfig = cdcKafkaConfig;
  }

  // When redisShortCircuit is enabled, skip Redis processing and ack the message
  // (the Kafka consumer handles it). Keeps the Redis consumer group current so toggling back
  // doesn't replay a large backlog. During transitions both consumers may briefly process
  // the same event — GitopsApplicationsCdcMessageHandler is idempotent, so this is safe.
  @Override
  protected boolean processMessage(Message message) {
    boolean shortCircuit = cdcKafkaConfig.getConsumer(CdcKafkaConfig.APPLICATIONS_CONSUMER)
                               .map(config -> config.isRedisShortCircuit())
                               .orElse(false);

    if (shortCircuit) {
      log.debug("Redis short-circuit enabled for applications, acking without processing");
      return true;
    }

    return super.processMessage(message);
  }
}
