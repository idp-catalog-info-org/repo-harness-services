/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.idp.catalog.events.BulkFieldUpdateEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class BulkFieldUpdateEventProducer {
  private final Producer eventProducer;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  public BulkFieldUpdateEventProducer(@Named(IDP_BULK_FIELD_UPDATE_EVENT) Producer eventProducer) {
    this.eventProducer = eventProducer;
  }

  public boolean publish(String operationId, String accountIdentifier) {
    try {
      BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder()
                                       .id(operationId)
                                       .accountIdentifier(accountIdentifier)
                                       .eventType("BULK_FIELD_UPDATE")
                                       .build();

      String eventJson = objectMapper.writeValueAsString(event);
      String eventId = eventProducer.send(Message.newBuilder()
                                              .putAllMetadata(Map.of("accountId", accountIdentifier, ENTITY_TYPE,
                                                  IDP_BULK_FIELD_UPDATE_EVENT, "action", CREATE_ACTION))
                                              .setData(ByteString.copyFromUtf8(eventJson))
                                              .build());

      log.info("Published bulk field update event: operationId={}, eventId={}", operationId, eventId);
      return true;
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize bulk field update event: operationId={}", operationId, e);
      return false;
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down, failed to publish bulk field update event: operationId={}", operationId, e);
      return false;
    }
  }
}
