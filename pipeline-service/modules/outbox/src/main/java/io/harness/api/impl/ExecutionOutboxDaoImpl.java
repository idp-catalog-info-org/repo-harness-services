/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.api.impl;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_CREATED_AT_ASC_SORT_ORDER;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import io.harness.ExecutionOutboxEvent;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.api.ExecutionOutboxDao;
import io.harness.ng.beans.PageRequest;
import io.harness.outbox.OutboxEvent.OutboxEventKeys;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.outbox.filter.OutboxEventsPerEventTypeCount;
import io.harness.outbox.filter.OutboxEventsPerEventTypeCount.OutboxEventsPerEventTypeCountKeys;
import io.harness.outbox.filter.OutboxMetricsFilter;
import io.harness.repositories.ExecutionOutboxEventRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ExecutionOutboxDaoImpl implements ExecutionOutboxDao {
  private ExecutionOutboxEventRepository executionOutboxEventRepository;

  public ExecutionOutboxEvent save(ExecutionOutboxEvent executionOutboxEvent) {
    return executionOutboxEventRepository.save(executionOutboxEvent);
  }

  public List<ExecutionOutboxEvent> list(OutboxEventFilter outboxMetricsFilter) {
    Assert.notNull(outboxMetricsFilter, "OutboxEventFilter must not be null!");
    return executionOutboxEventRepository.findAll(getEventListCriteria(),
        getPageRequest(PageRequest.builder()
                           .pageIndex(0)
                           .pageSize(outboxMetricsFilter.getMaximumEventsPolled())
                           .sortOrders(DEFAULT_CREATED_AT_ASC_SORT_ORDER)
                           .build()));
  }

  public long count(OutboxMetricsFilter outboxMetricsFilter) {
    return executionOutboxEventRepository.count(getEventCountCriteria(outboxMetricsFilter));
  }

  public Map<String, Long> countPerEventType(OutboxMetricsFilter outboxMetricsFilter) {
    MatchOperation matchStage = Aggregation.match(getEventCountCriteria(outboxMetricsFilter));
    SortOperation sortStage = sort(Sort.by(OutboxEventKeys.eventType));
    GroupOperation groupByOrganizationStage =
        group(OutboxEventKeys.eventType).count().as(OutboxEventsPerEventTypeCountKeys.count);
    ProjectionOperation projectionStage =
        project().and(MONGODB_ID).as(OutboxEventKeys.eventType).andInclude(OutboxEventsPerEventTypeCountKeys.count);
    Map<String, Long> result = new HashMap<>();
    executionOutboxEventRepository
        .aggregate(newAggregation(matchStage, sortStage, groupByOrganizationStage, projectionStage),
            OutboxEventsPerEventTypeCount.class)
        .getMappedResults()
        .forEach(outboxEventsPerEventTypeCount
            -> result.put(outboxEventsPerEventTypeCount.getEventType(), outboxEventsPerEventTypeCount.getCount()));
    return result;
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
    if (outboxMetricsFilter != null && outboxMetricsFilter.getBlocked() != null) {
      criteria = criteria.and(OutboxEventKeys.blocked).is(outboxMetricsFilter.getBlocked());
    }
    return criteria;
  }

  public boolean delete(String outboxEventId) {
    executionOutboxEventRepository.deleteById(outboxEventId);
    return true;
  }
}