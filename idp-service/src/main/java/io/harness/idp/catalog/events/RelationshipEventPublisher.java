/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.events;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class RelationshipEventPublisher {
  private final Producer relationshipProcessingEventProducer;
  private final ObjectMapper objectMapper;

  @Inject
  public RelationshipEventPublisher(
      @Named(IDP_RELATIONSHIP_PROCESSING_EVENT) Producer relationshipProcessingEventProducer) {
    this.relationshipProcessingEventProducer = relationshipProcessingEventProducer;
    this.objectMapper = new ObjectMapper();
  }

  public void publishEvents(List<RelationshipProcessingEvent> events) {
    for (RelationshipProcessingEvent event : events) {
      publishEvent(event);
    }
  }

  public void publishEvent(RelationshipProcessingEvent event) {
    try {
      String eventPayload = objectMapper.writeValueAsString(event);
      sendToRedisStream(event, eventPayload);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize relationship processing event for entityId={}: {}", event.getEntityId(),
          e.getMessage(), e);
    } catch (Exception e) {
      log.error("Failed to publish relationship processing event for entityId={}: {}", event.getEntityId(),
          e.getMessage(), e);
    }
  }

  private void sendToRedisStream(RelationshipProcessingEvent event, String eventPayload) {
    String eventId = relationshipProcessingEventProducer.send(
        Message.newBuilder()
            .putAllMetadata(Map.of("accountIdentifier", event.getAccountIdentifier(), ENTITY_TYPE,
                IDP_RELATIONSHIP_PROCESSING_EVENT, ACTION, CREATE_ACTION, "entityId", event.getEntityId(), "eventType",
                event.getEventType().name()))
            .setData(ByteString.copyFromUtf8(eventPayload))
            .build());
    log.info("Published relationship processing event {} for entityId={} eventType={}", eventId, event.getEntityId(),
        event.getEventType());
  }
}
