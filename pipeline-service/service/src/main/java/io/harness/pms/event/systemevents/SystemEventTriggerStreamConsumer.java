/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.event.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.SYSTEM_EVENT_TRIGGER_STREAM;
import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.ConsumerPreStartCheckUtils;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class SystemEventTriggerStreamConsumer extends PmsAbstractRedisConsumer<SystemEventTriggerStreamListener> {
  private final QueueController queueController;
  private final AtomicBoolean shouldStop = new AtomicBoolean(false);

  @Inject
  public SystemEventTriggerStreamConsumer(@Named(SYSTEM_EVENT_TRIGGER_STREAM) Consumer redisConsumer,
      SystemEventTriggerStreamListener listener, @Named("pmsEventsCache") Cache<String, Integer> eventsCache,
      QueueController queueController, @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
    super(redisConsumer, listener, eventsCache, queueController, executorService);
    this.queueController = queueController;
  }

  @Override
  public void run() {
    log.info("Started the consumer for system event trigger stream {}", this.getClass().getSimpleName());
    Thread.currentThread().setName(this.getClass().getSimpleName() + "-handler-" + generateUuid());
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
      while (!Thread.currentThread().isInterrupted() && !shouldStop.get()) {
        ConsumerPreStartCheckUtils.checkPodAndQueueState(queueController);
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("System event trigger stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  public void shutDown() {
    shouldStop.set(true);
  }
}
