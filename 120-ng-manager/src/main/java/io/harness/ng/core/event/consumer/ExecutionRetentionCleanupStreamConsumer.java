/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.consumer;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Stream consumer for execution retention cleanup events.
 * Consumes events published by pipeline-service when executions are cleaned up
 * and triggers TimescaleDB cleanup via the listener.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(CDC)
@Slf4j
public class ExecutionRetentionCleanupStreamConsumer extends RedisTraceConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private static final String LOG_PREFIX = "[EXECUTION_RETENTION_CLEANUP_CONSUMER]";

  private final Consumer redisConsumer;
  private final List<MessageListener> messageListenersList;
  private final QueueController queueController;

  @Inject
  public ExecutionRetentionCleanupStreamConsumer(@Named(EXECUTION_RETENTION_CLEANUP_EVENT) Consumer redisConsumer,
      @Named(EXECUTION_RETENTION_CLEANUP_EVENT) MessageListener messageListener, QueueController queueController) {
    this.redisConsumer = redisConsumer;
    this.queueController = queueController;
    messageListenersList = new ArrayList<>();
    messageListenersList.add(messageListener);
  }

  @Override
  public void run() {
    log.info("{} Started the consumer for execution retention cleanup events", LOG_PREFIX);
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info("{} Consumer is not running on primary deployment, will try again after some time...", LOG_PREFIX);
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("{} Consumer unexpectedly stopped", LOG_PREFIX, ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("{} Events framework is down. Retrying again...", LOG_PREFIX, e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        redisConsumer.acknowledge(messageId);
      }
    }
  }

  @Override
  protected boolean processMessage(Message message) {
    AtomicBoolean success = new AtomicBoolean(true);
    messageListenersList.forEach(messageListener -> {
      if (!messageListener.handleMessage(message)) {
        success.set(false);
      }
    });

    return success.get();
  }
}
