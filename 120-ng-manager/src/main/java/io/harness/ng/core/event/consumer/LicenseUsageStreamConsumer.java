/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.consumer;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class LicenseUsageStreamConsumer extends RedisTraceConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 30;
  private final Consumer eventConsumer;
  private final MessageListener messageListener;
  private final QueueController queueController;

  @Inject
  public LicenseUsageStreamConsumer(@Named(LICENSES_USAGE_REDIS_EVENT_CONSUMER) Consumer eventConsumer,
      @Named(LICENSES_USAGE_REDIS_EVENT_CONSUMER) MessageListener licenseUsageStreamListener,
      QueueController queueController) {
    this.eventConsumer = eventConsumer;
    this.queueController = queueController;
    messageListener = licenseUsageStreamListener;
  }

  @Override
  public void run() {
    log.info("Started the consumer for " + LICENSES_USAGE_REDIS_EVENT_CONSUMER + " stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info(
              "LicenseUsage stream consumer is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      log.error(LICENSES_USAGE_REDIS_EVENT_CONSUMER + " stream consumer unexpectedly interrupted", ex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error(LICENSES_USAGE_REDIS_EVENT_CONSUMER + " stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error(
          "Events framework is down for " + LICENSES_USAGE_REDIS_EVENT_CONSUMER + " stream consumer. Retrying...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    String messageId;
    boolean messageProcessed;
    List<Message> messages = eventConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));

    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        eventConsumer.acknowledge(messageId);
      }
    }
  }

  @Override
  protected boolean processMessage(Message message) {
    if (!messageListener.handleMessage(message)) {
      return false;
    }
    return true;
  }
}
