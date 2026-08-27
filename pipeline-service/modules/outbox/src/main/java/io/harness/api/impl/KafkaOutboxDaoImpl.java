/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.api.impl;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_CREATED_AT_ASC_SORT_ORDER;
import static io.harness.utils.PageUtils.getPageRequest;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import io.harness.KafkaOutboxEvent;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.api.KafkaOutboxDao;
import io.harness.event.Event;
import io.harness.exception.UnexpectedException;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.beans.PageRequest;
import io.harness.outbox.OutboxEvent.OutboxEventKeys;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.outbox.filter.OutboxEventsPerEventTypeCount;
import io.harness.outbox.filter.OutboxEventsPerEventTypeCount.OutboxEventsPerEventTypeCountKeys;
import io.harness.outbox.filter.OutboxMetricsFilter;
import io.harness.repositories.KafkaOutboxEventRepository;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.Assert;

@OwnedBy(PIPELINE)
@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class KafkaOutboxDaoImpl implements KafkaOutboxDao {
  private KafkaOutboxEventRepository kafkaOutboxEventRepository;
  private final ObjectMapper objectMapper;

  @Inject
  public KafkaOutboxDaoImpl(KafkaOutboxEventRepository kafkaOutboxEventRepository) {
    this.kafkaOutboxEventRepository = kafkaOutboxEventRepository;
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  }

  @Override
  public KafkaOutboxEvent save(KafkaOutboxEvent kafkaOutboxEvent) {
    return kafkaOutboxEventRepository.save(kafkaOutboxEvent);
  }

  @Override
  public KafkaOutboxEvent save(Event event, String topic) {
    return kafkaOutboxEventRepository.save(createKafkaOutboxEvent(event, topic));
  }

  @Override
  public List<KafkaOutboxEvent> list(OutboxEventFilter outboxEventFilter) {
    Assert.notNull(outboxEventFilter, "OutboxEventFilter must not be null!");
    return kafkaOutboxEventRepository.findAll(getEventListCriteria(),
        getPageRequest(PageRequest.builder()
                           .pageIndex(0)
                           .pageSize(outboxEventFilter.getMaximumEventsPolled())
                           .sortOrders(DEFAULT_CREATED_AT_ASC_SORT_ORDER)
                           .build()));
  }

  @Override
  public long count(OutboxMetricsFilter outboxMetricsFilter) {
    return kafkaOutboxEventRepository.count(getEventCountCriteria(outboxMetricsFilter));
  }

  @Override
  public Map<String, Long> countPerEventType(OutboxMetricsFilter outboxMetricsFilter) {
    MatchOperation matchStage = Aggregation.match(getEventCountCriteria(outboxMetricsFilter));
    SortOperation sortStage = sort(Sort.by(OutboxEventKeys.eventType));
    GroupOperation groupByEventTypeStage =
        group(OutboxEventKeys.eventType).count().as(OutboxEventsPerEventTypeCountKeys.count);
    ProjectionOperation projectionStage =
        project().and(MONGODB_ID).as(OutboxEventKeys.eventType).andInclude(OutboxEventsPerEventTypeCountKeys.count);
    Map<String, Long> result = new HashMap<>();
    kafkaOutboxEventRepository
        .aggregate(newAggregation(matchStage, sortStage, groupByEventTypeStage, projectionStage),
            OutboxEventsPerEventTypeCount.class)
        .getMappedResults()
        .forEach(outboxEventsPerEventTypeCount
            -> result.put(outboxEventsPerEventTypeCount.getEventType(), outboxEventsPerEventTypeCount.getCount()));
    return result;
  }

  @Override
  public boolean delete(String outboxEventId) {
    kafkaOutboxEventRepository.deleteById(outboxEventId);
    return true;
  }

  private Criteria getEventListCriteria() {
    Criteria criteria = new Criteria();
    Criteria blockedNotTrueCriteria = Criteria.where(OutboxEventKeys.blocked).ne(Boolean.TRUE);
    Criteria blockedTrueCriteria = Criteria.where(OutboxEventKeys.blocked)
                                       .is(Boolean.TRUE)
                                       .and(OutboxEventKeys.nextUnblockAttemptAt)
                                       .lt(Instant.now());
    criteria.orOperator(blockedNotTrueCriteria, blockedTrueCriteria);
    return criteria;
  }

  private Criteria getEventCountCriteria(OutboxMetricsFilter outboxMetricsFilter) {
    Criteria criteria = new Criteria();

    if (outboxMetricsFilter != null) {
      if (outboxMetricsFilter.getBlocked() != null) {
        criteria = criteria.and(OutboxEventKeys.blocked).is(outboxMetricsFilter.getBlocked());
      }
    }
    return criteria;
  }

  private KafkaOutboxEvent createKafkaOutboxEvent(Event event, String topic) {
    String eventData;
    try {
      eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(event);
    } catch (JsonProcessingException exception) {
      throw new UnexpectedException(
          "JsonProcessingException occurred while serializing eventData for Kafka outbox.", exception);
    }
    return KafkaOutboxEvent.builder()
        .resourceScope(event.getResourceScope())
        .resource(event.getResource())
        .eventData(eventData)
        .eventType(event.getEventType())
        .topic(topic)
        .globalContext(GlobalContextManager.obtainGlobalContext())
        .build();
  }
}
