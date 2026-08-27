/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.event.MessageListener;
import io.harness.pms.events.base.ConsumerPreStartCheckUtils;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class PMSEntityCRUDStreamConsumer extends RedisTraceConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private final Consumer redisConsumer;
  private final List<MessageListener> messageListenersList;
  private final QueueController queueController;
  private final ExecutorService pipelineExecutorService;

  @Inject
  public PMSEntityCRUDStreamConsumer(@Named(ENTITY_CRUD) Consumer redisConsumer,
      @Named(PIPELINE_ENTITY + ENTITY_CRUD) MessageListener pipelineEntityCRUDStreamListener,
      @Named(PROJECT_ENTITY + ENTITY_CRUD) MessageListener projectEntityCrudStreamListener,
      @Named(ACCOUNT_ENTITY + ENTITY_CRUD) MessageListener accountEntityCrudStreamListener,
      @Named(SETTINGS + ENTITY_CRUD) MessageListener pipelineSettingCRUDStreamListener, QueueController queueController,
      @Named("PipelineExecutorService") ExecutorService pipelineExecutorService) {
    this.redisConsumer = redisConsumer;
    messageListenersList = new ArrayList<>();
    messageListenersList.add(pipelineEntityCRUDStreamListener);
    messageListenersList.add(projectEntityCrudStreamListener);
    messageListenersList.add(accountEntityCrudStreamListener);
    messageListenersList.add(pipelineSettingCRUDStreamListener);
    this.queueController = queueController;
    this.pipelineExecutorService = pipelineExecutorService;
  }

  @Override
  public void run() {
    log.info("Started the consumer for PMS entity crud stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
      SourcePrincipalContextBuilder.setSourcePrincipal(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        ConsumerPreStartCheckUtils.checkPodAndQueueState(queueController);
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("PMS Entity crud stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for PMS Entity crud stream consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    List<String> messageIds = messages.stream().map(Message::getId).collect(Collectors.toList());
    if (messageIds.size() > 0) {
      log.info("Received events with eventIds [{}] from redis", messageIds);
    }
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
    GlobalContext context = GlobalContextManager.obtainGlobalContextCopy();
    pipelineExecutorService.submit(() -> {
      try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.initGlobalContextGuard(context)) {
        messageListenersList.forEach(messageListener -> {
          if (!messageListener.handleMessage(message)) {
            log.error("Failed to process event with event id : {}", message.getId());
          }
        });
      }
    });
    return success.get();
  }
}
