/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.eventsframework.EventsFrameworkConstants.GITOPS_APPSYNC_REDIS_EVENT_CONSUMER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.debezium.redisconsumer.DebeziumAbstractRedisConsumer;
import io.harness.eventsframework.api.Consumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(GITOPS)
@Singleton
public class GitopsAppsyncRedisEventConsumer extends DebeziumAbstractRedisConsumer {
  @Inject
  public GitopsAppsyncRedisEventConsumer(@Named(GITOPS_APPSYNC_REDIS_EVENT_CONSUMER) Consumer redisConsumer,
      QueueController queueController, GitopsAppsyncRedisEventHandler eventHandler,
      @Named("debeziumEventsCache") Cache<String, Long> eventsCache) {
    super(redisConsumer, queueController, eventHandler, eventsCache);
  }
}
