/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.events;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.idp.catalog.entities.RelationshipEventType;
import io.harness.rule.Owner;

import java.util.Arrays;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class RelationshipEventPublisherTest extends CategoryTest {
  @Mock Producer producer;

  RelationshipEventPublisher publisher;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    publisher = new RelationshipEventPublisher(producer);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPublishEvent_SendsToRedis() {
    RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                            .entityId("entity123")
                                            .accountIdentifier("acc1")
                                            .eventType(RelationshipEventType.ESTABLISH)
                                            .timestamp(System.currentTimeMillis())
                                            .build();

    when(producer.send(any(Message.class))).thenReturn("event-id-1");

    publisher.publishEvent(event);

    verify(producer).send(any(Message.class));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPublishEvents_PublishesMultiple() {
    RelationshipProcessingEvent event1 = RelationshipProcessingEvent.builder()
                                             .entityId("entity1")
                                             .accountIdentifier("acc1")
                                             .eventType(RelationshipEventType.ESTABLISH)
                                             .timestamp(System.currentTimeMillis())
                                             .build();

    RelationshipProcessingEvent event2 = RelationshipProcessingEvent.builder()
                                             .entityId("entity2")
                                             .accountIdentifier("acc1")
                                             .eventType(RelationshipEventType.ESTABLISH)
                                             .timestamp(System.currentTimeMillis())
                                             .build();

    when(producer.send(any(Message.class))).thenReturn("event-id");

    publisher.publishEvents(Arrays.asList(event1, event2));

    verify(producer, times(2)).send(any(Message.class));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPublishEvent_RedisMessageContainsCorrectMetadata() {
    RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                            .entityId("entity456")
                                            .accountIdentifier("acc2")
                                            .eventType(RelationshipEventType.DISBAND)
                                            .timestamp(System.currentTimeMillis())
                                            .build();

    when(producer.send(any(Message.class))).thenReturn("event-id-3");

    publisher.publishEvent(event);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(messageCaptor.capture());
    Message sentMessage = messageCaptor.getValue();
    assertThat(sentMessage.getMetadataMap().get("accountIdentifier")).isEqualTo("acc2");
    assertThat(sentMessage.getMetadataMap().get("entityId")).isEqualTo("entity456");
    assertThat(sentMessage.getMetadataMap().get("eventType")).isEqualTo("DISBAND");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPublishEvent_ProducerFailure_DoesNotThrow() {
    RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                            .entityId("entity789")
                                            .accountIdentifier("acc1")
                                            .eventType(RelationshipEventType.ESTABLISH)
                                            .timestamp(System.currentTimeMillis())
                                            .build();

    when(producer.send(any(Message.class))).thenThrow(new RuntimeException("Redis connection failed"));

    publisher.publishEvent(event);

    verify(producer).send(any(Message.class));
  }
}
