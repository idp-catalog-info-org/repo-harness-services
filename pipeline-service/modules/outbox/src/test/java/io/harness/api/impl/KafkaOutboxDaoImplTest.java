/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import io.harness.repositories.KafkaOutboxEventRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class KafkaOutboxDaoImplTest extends CategoryTest {
  @Mock private KafkaOutboxEventRepository kafkaOutboxEventRepository;
  @Mock private OutboxEventFilter outboxEventFilter;
  @Mock private OutboxMetricsFilter outboxMetricsFilter;

  private KafkaOutboxDaoImpl kafkaOutboxDaoImpl;

  private static final String TOPIC = "test-topic";
  private static final String EVENT_ID = "eventId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  private KafkaOutboxEvent testEvent;
  private ProjectScope scope;
  private Resource resource;
  private TestEvent testEventImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    kafkaOutboxDaoImpl = new KafkaOutboxDaoImpl(kafkaOutboxEventRepository);

    scope = new ProjectScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    resource = Resource.builder().identifier("testResource").type("TEST").build();

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
    when(outboxEventFilter.getMaximumEventsPolled()).thenReturn(100);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSaveKafkaOutboxEvent() {
    when(kafkaOutboxEventRepository.save(any(KafkaOutboxEvent.class))).thenReturn(testEvent);

    KafkaOutboxEvent result = kafkaOutboxDaoImpl.save(testEvent);

    assertThat(result).isEqualTo(testEvent);
    verify(kafkaOutboxEventRepository).save(testEvent);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSaveEventWithTopic() {
    when(kafkaOutboxEventRepository.save(any(KafkaOutboxEvent.class))).thenReturn(testEvent);

    KafkaOutboxEvent result = kafkaOutboxDaoImpl.save(testEventImpl, TOPIC);

    assertThat(result).isNotNull();
    verify(kafkaOutboxEventRepository).save(any(KafkaOutboxEvent.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testListEvents() {
    List<KafkaOutboxEvent> expectedEvents = Arrays.asList(testEvent);
    when(kafkaOutboxEventRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(expectedEvents);

    List<KafkaOutboxEvent> result = kafkaOutboxDaoImpl.list(outboxEventFilter);

    assertThat(result).isEqualTo(expectedEvents);
    assertThat(result).hasSize(1);
    verify(kafkaOutboxEventRepository).findAll(any(Criteria.class), any(Pageable.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCountEvents() {
    long expectedCount = 5L;
    when(kafkaOutboxEventRepository.count(any(Criteria.class))).thenReturn(expectedCount);

    long result = kafkaOutboxDaoImpl.count(outboxMetricsFilter);

    assertThat(result).isEqualTo(expectedCount);
    verify(kafkaOutboxEventRepository).count(any(Criteria.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCountEventsWithNullFilter() {
    long expectedCount = 3L;
    when(kafkaOutboxEventRepository.count(any(Criteria.class))).thenReturn(expectedCount);

    long result = kafkaOutboxDaoImpl.count(null);

    assertThat(result).isEqualTo(expectedCount);
    verify(kafkaOutboxEventRepository).count(any(Criteria.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCountEventsWithBlockedFilter() {
    when(outboxMetricsFilter.getBlocked()).thenReturn(true);
    long expectedCount = 2L;
    when(kafkaOutboxEventRepository.count(any(Criteria.class))).thenReturn(expectedCount);

    long result = kafkaOutboxDaoImpl.count(outboxMetricsFilter);

    assertThat(result).isEqualTo(expectedCount);
    verify(kafkaOutboxEventRepository).count(any(Criteria.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteEvent() {
    kafkaOutboxDaoImpl.delete(EVENT_ID);

    verify(kafkaOutboxEventRepository).deleteById(EVENT_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteEventReturnsTrue() {
    boolean result = kafkaOutboxDaoImpl.delete(EVENT_ID);

    assertThat(result).isTrue();
    verify(kafkaOutboxEventRepository).deleteById(EVENT_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testListEventsWithNullFilter() {
    assertThatThrownBy(() -> kafkaOutboxDaoImpl.list(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("OutboxEventFilter must not be null!");
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
