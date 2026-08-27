/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class KafkaOutboxEventTest extends CategoryTest {
  private static final String TOPIC = "test-topic";
  private static final String EVENT_TYPE = "TEST_EVENT";
  private static final String EVENT_DATA = "{\"test\": \"data\"}";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testKafkaOutboxEventBuilder() {
    ProjectScope scope = new ProjectScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    Resource resource = Resource.builder().identifier("testResource").type("TEST").build();

    Instant now = Instant.now();
    KafkaOutboxEvent event = KafkaOutboxEvent.builder()
                                 .topic(TOPIC)
                                 .retryCount(0)
                                 .blocked(false)
                                 .lastUpdatedAt(now)
                                 .eventType(EVENT_TYPE)
                                 .eventData(EVENT_DATA)
                                 .resourceScope(scope)
                                 .resource(resource)
                                 .build();

    assertThat(event.getTopic()).isEqualTo(TOPIC);
    assertThat(event.getRetryCount()).isEqualTo(0);
    assertThat(event.getBlocked()).isFalse();
    assertThat(event.getLastUpdatedAt()).isEqualTo(now);
    assertThat(event.getEventType()).isEqualTo(EVENT_TYPE);
    assertThat(event.getEventData()).isEqualTo(EVENT_DATA);
    assertThat(event.getResourceScope()).isEqualTo(scope);
    assertThat(event.getResource()).isEqualTo(resource);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testKafkaOutboxEventWithBlockedState() {
    KafkaOutboxEvent event = KafkaOutboxEvent.builder()
                                 .topic(TOPIC)
                                 .retryCount(3)
                                 .blocked(true)
                                 .eventType(EVENT_TYPE)
                                 .eventData(EVENT_DATA)
                                 .build();

    assertThat(event.getTopic()).isEqualTo(TOPIC);
    assertThat(event.getRetryCount()).isEqualTo(3);
    assertThat(event.getBlocked()).isTrue();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testMongoIndexes() {
    List<MongoIndex> indexes = KafkaOutboxEvent.mongoIndexes();

    assertThat(indexes).isNotNull();
    assertThat(indexes).hasSize(2);

    // Verify compound index names
    assertThat(indexes.get(0).getName()).isEqualTo("eventType_blocked_createdAt_nextUnblockAttemptAt_kafka_outbox_Idx");
    assertThat(indexes.get(1).getName()).isEqualTo("topic_retryCount_kafka_outbox_Idx");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    KafkaOutboxEvent event = new KafkaOutboxEvent();
    assertThat(event).isNotNull();
  }
}
