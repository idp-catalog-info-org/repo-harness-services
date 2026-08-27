/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

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
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.outbox.filter.OutboxEventsPerEventTypeCount;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class KafkaOutboxEventRepositoryTest extends CategoryTest {
  @Mock private KafkaOutboxEventRepository kafkaOutboxEventRepository;
  @Mock private AggregationResults<OutboxEventsPerEventTypeCount> aggregationResults;

  private static final String TOPIC = "test-topic";
  private static final String EVENT_ID = "eventId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  private KafkaOutboxEvent testEvent;

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
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSaveEvent() {
    when(kafkaOutboxEventRepository.save(any(KafkaOutboxEvent.class))).thenReturn(testEvent);

    KafkaOutboxEvent result = kafkaOutboxEventRepository.save(testEvent);

    assertThat(result).isEqualTo(testEvent);
    verify(kafkaOutboxEventRepository).save(testEvent);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testFindById() {
    when(kafkaOutboxEventRepository.findById(anyString())).thenReturn(Optional.of(testEvent));

    Optional<KafkaOutboxEvent> result = kafkaOutboxEventRepository.findById(EVENT_ID);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(testEvent);
    verify(kafkaOutboxEventRepository).findById(EVENT_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testFindByIdNotFound() {
    when(kafkaOutboxEventRepository.findById(anyString())).thenReturn(Optional.empty());

    Optional<KafkaOutboxEvent> result = kafkaOutboxEventRepository.findById(EVENT_ID);

    assertThat(result).isEmpty();
    verify(kafkaOutboxEventRepository).findById(EVENT_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testFindAllWithCriteria() {
    List<KafkaOutboxEvent> expectedEvents = Arrays.asList(testEvent);
    when(kafkaOutboxEventRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(expectedEvents);

    List<KafkaOutboxEvent> result = kafkaOutboxEventRepository.findAll(new Criteria(), Pageable.unpaged());

    assertThat(result).isEqualTo(expectedEvents);
    assertThat(result).hasSize(1);
    verify(kafkaOutboxEventRepository).findAll(any(Criteria.class), any(Pageable.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCountWithCriteria() {
    long expectedCount = 5L;
    when(kafkaOutboxEventRepository.count(any(Criteria.class))).thenReturn(expectedCount);

    long result = kafkaOutboxEventRepository.count(new Criteria());

    assertThat(result).isEqualTo(expectedCount);
    verify(kafkaOutboxEventRepository).count(any(Criteria.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteById() {
    kafkaOutboxEventRepository.deleteById(EVENT_ID);

    verify(kafkaOutboxEventRepository).deleteById(EVENT_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testAggregation() {
    when(kafkaOutboxEventRepository.aggregate(any(Aggregation.class), any(Class.class))).thenReturn(aggregationResults);

    // Create a proper aggregation with match and group operations
    MatchOperation matchOperation = Aggregation.match(Criteria.where("accountIdentifier").is(ACCOUNT_ID));
    GroupOperation groupOperation = Aggregation.group("eventType").count().as("count");

    AggregationResults<OutboxEventsPerEventTypeCount> result = kafkaOutboxEventRepository.aggregate(
        Aggregation.newAggregation(matchOperation, groupOperation), OutboxEventsPerEventTypeCount.class);

    assertThat(result).isEqualTo(aggregationResults);
    verify(kafkaOutboxEventRepository).aggregate(any(Aggregation.class), any(Class.class));
  }
}
