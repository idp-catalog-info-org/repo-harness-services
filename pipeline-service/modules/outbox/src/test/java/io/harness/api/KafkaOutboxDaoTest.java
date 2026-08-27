/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.KafkaOutboxEvent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.outbox.filter.OutboxMetricsFilter;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class KafkaOutboxDaoTest extends CategoryTest {
  @Mock private KafkaOutboxDao kafkaOutboxDao;
  @Mock private OutboxEventFilter outboxEventFilter;
  @Mock private OutboxMetricsFilter outboxMetricsFilter;

  private static final String TOPIC = "test-topic";
  private static final String EVENT_ID = "eventId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  private KafkaOutboxEvent testEvent;
  private TestEvent testEventImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    ProjectScope scope = new ProjectScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    Resource resource = Resource.builder().identifier("testResource").type("TEST").build();

    testEvent = KafkaOutboxEvent.builder()
                    .topic(TOPIC)
                    .retryCount(0)
                    .blocked(false)
                    .eventType("TEST_EVENT")
                    .eventData("{\"test\": \"data\"}")
                    .resourceScope(scope)
                    .resource(resource)
                    .build();

    testEventImpl = new TestEvent(scope, resource, "TEST_EVENT");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSaveKafkaOutboxEvent() {
    when(kafkaOutboxDao.save(any(KafkaOutboxEvent.class))).thenReturn(testEvent);

    KafkaOutboxEvent result = kafkaOutboxDao.save(testEvent);

    assertThat(result).isEqualTo(testEvent);
    verify(kafkaOutboxDao).save(testEvent);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSaveEventWithTopic() {
    when(kafkaOutboxDao.save(any(Event.class), anyString())).thenReturn(testEvent);

    KafkaOutboxEvent result = kafkaOutboxDao.save(testEventImpl, TOPIC);

    assertThat(result).isEqualTo(testEvent);
    verify(kafkaOutboxDao).save(testEventImpl, TOPIC);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testListEvents() {
    List<KafkaOutboxEvent> expectedEvents = Arrays.asList(testEvent);
    when(kafkaOutboxDao.list(any(OutboxEventFilter.class))).thenReturn(expectedEvents);

    List<KafkaOutboxEvent> result = kafkaOutboxDao.list(outboxEventFilter);

    assertThat(result).isEqualTo(expectedEvents);
    assertThat(result).hasSize(1);
    verify(kafkaOutboxDao).list(outboxEventFilter);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCountEvents() {
    long expectedCount = 5L;
    when(kafkaOutboxDao.count(any(OutboxMetricsFilter.class))).thenReturn(expectedCount);

    long result = kafkaOutboxDao.count(outboxMetricsFilter);

    assertThat(result).isEqualTo(expectedCount);
    verify(kafkaOutboxDao).count(outboxMetricsFilter);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCountPerEventType() {
    Map<String, Long> expectedCounts = Map.of("TEST_EVENT", 3L, "ANOTHER_EVENT", 2L);
    when(kafkaOutboxDao.countPerEventType(any(OutboxMetricsFilter.class))).thenReturn(expectedCounts);

    Map<String, Long> result = kafkaOutboxDao.countPerEventType(outboxMetricsFilter);

    assertThat(result).isEqualTo(expectedCounts);
    assertThat(result).hasSize(2);
    assertThat(result.get("TEST_EVENT")).isEqualTo(3L);
    assertThat(result.get("ANOTHER_EVENT")).isEqualTo(2L);
    verify(kafkaOutboxDao).countPerEventType(outboxMetricsFilter);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteEvent() {
    when(kafkaOutboxDao.delete(anyString())).thenReturn(true);

    boolean result = kafkaOutboxDao.delete(EVENT_ID);

    assertThat(result).isTrue();
    verify(kafkaOutboxDao).delete(EVENT_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteEventFailure() {
    when(kafkaOutboxDao.delete(anyString())).thenReturn(false);

    boolean result = kafkaOutboxDao.delete(EVENT_ID);

    assertThat(result).isFalse();
    verify(kafkaOutboxDao).delete(EVENT_ID);
  }

  // Test implementation of Event interface for testing purposes
  private static class TestEvent implements Event {
    private final ResourceScope resourceScope;
    private final Resource resource;
    private final String eventType;

    public TestEvent(ResourceScope resourceScope, Resource resource, String eventType) {
      this.resourceScope = resourceScope;
      this.resource = resource;
      this.eventType = eventType;
    }

    @Override
    public ResourceScope getResourceScope() {
      return resourceScope;
    }

    @Override
    public Resource getResource() {
      return resource;
    }

    @Override
    public String getEventType() {
      return eventType;
    }
  }
}
