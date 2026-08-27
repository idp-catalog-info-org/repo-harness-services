/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.idp.catalog.utils.Constants.RELATIONSHIP_LOCK_PREFIX;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.idp.catalog.entities.RelationshipTask;
import io.harness.idp.catalog.entities.TaskStatus;
import io.harness.idp.catalog.events.RelationshipProcessingEvent;
import io.harness.idp.catalog.processor.RelationshipEventProcessor;
import io.harness.idp.catalog.repositories.RelationshipTaskRepository;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class RelationshipProcessingEventConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "RelationshipProcessingEventConsumer";

  @Inject private RelationshipEventProcessor relationshipEventProcessor;
  @Inject private RelationshipTaskRepository relationshipTaskRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  public RelationshipProcessingEventConsumer(@Named(IDP_RELATIONSHIP_PROCESSING_EVENT) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message != null && message.hasMessage()) {
      try {
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        String entityType = metadata.get(ENTITY_TYPE);
        boolean isValid = entityTypeAndActionValidation(
            CONSUMER_NAME, message, IDP_RELATIONSHIP_PROCESSING_EVENT, List.of(CREATE_ACTION));
        if (isValid) {
          ByteString data = message.getMessage().getData();
          RelationshipProcessingEvent event =
              objectMapper.readValue(data.toStringUtf8(), RelationshipProcessingEvent.class);
          String lockName = RELATIONSHIP_LOCK_PREFIX + event.getEntityId();
          return lockAndProcessData(lockName, entityType, data);
        }
      } catch (Exception ex) {
        log.error("Error in processing message with id: {} in {} consumer. Error = {}", message.getId(), CONSUMER_NAME,
            ex.getMessage(), ex);
        return false;
      }
      log.info("Processed messageId = {} in {} consumer", message.getId(), CONSUMER_NAME);
    }
    return true;
  }

  @Override
  protected void processInternal(String entityType, ByteString data) throws Exception {
    RelationshipProcessingEvent event = objectMapper.readValue(data.toStringUtf8(), RelationshipProcessingEvent.class);
    String entityId = event.getEntityId();

    try {
      relationshipEventProcessor.processEvent(event);
      log.info(
          "Successfully processed relationship event for entityId={} eventType={}", entityId, event.getEventType());
    } catch (Exception e) {
      log.error("Failed to process relationship event for entityId={}: {}", entityId, e.getMessage(), e);
      createFailedTask(event, data.toStringUtf8(), e.getMessage());
      throw e;
    }
  }

  private void createFailedTask(RelationshipProcessingEvent event, String eventPayload, String errorMessage) {
    long now = System.currentTimeMillis();
    RelationshipTask task = RelationshipTask.builder()
                                .entityId(event.getEntityId())
                                .accountIdentifier(event.getAccountIdentifier())
                                .eventType(event.getEventType())
                                .status(TaskStatus.FAILED)
                                .retryCount(0)
                                .createdAt(now)
                                .lastAttemptAt(now)
                                .nextRetryAt(relationshipEventProcessor.calculateNextRetryTime(1))
                                .errorMessage(errorMessage)
                                .eventPayload(eventPayload)
                                .build();
    relationshipTaskRepository.save(task);
  }
}
