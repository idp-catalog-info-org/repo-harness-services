/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.notificationEventLog;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entity.eventlog.NotificationEventLog;
import io.harness.entity.eventlog.NotificationEventLog.NotificationEventLogKeys;
import io.harness.notification.PipelineEventType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class NotificationEventLogRepositoryCustomImpl implements NotificationEventLogRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public boolean checkIfEventExists(
      String planExecutionId, String nodeExecutionId, PipelineEventType pipelineEventType) {
    Criteria criteria = Criteria.where(NotificationEventLogKeys.planExecutionId).is(planExecutionId);

    if (nodeExecutionId != null) {
      criteria.and(NotificationEventLogKeys.nodeExecutionId).is(nodeExecutionId);
    }
    if (pipelineEventType != null) {
      criteria.and(NotificationEventLogKeys.pipelineEventType).is(pipelineEventType);
    }
    Query query = new Query(criteria);
    return mongoTemplate.exists(query, NotificationEventLog.class);
  }

  @Override
  public List<NotificationEventLog> getNotificationsSent(String planExecutionId, List<String> nodeExecutionIds) {
    Criteria planCriteria = Criteria.where(NotificationEventLogKeys.planExecutionId).is(planExecutionId);

    if (isNotEmpty(nodeExecutionIds)) {
      List<Criteria> orCriteria = new ArrayList<>();
      orCriteria.add(Criteria.where(NotificationEventLogKeys.nodeExecutionId).in(nodeExecutionIds));
      for (String id : nodeExecutionIds) {
        orCriteria.add(
            Criteria.where(NotificationEventLogKeys.nodeExecutionId).regex("^" + Pattern.quote(id) + ":[0-9]+$"));
      }
      Criteria combined =
          new Criteria().andOperator(planCriteria, new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
      Query query = new Query(combined);
      return mongoTemplate.find(query, NotificationEventLog.class);
    }

    Query query = new Query(planCriteria);
    return mongoTemplate.find(query, NotificationEventLog.class);
  }

  @Override
  public Optional<NotificationEventLog> findMostRecentByEventType(
      String planExecutionId, PipelineEventType pipelineEventType) {
    Criteria criteria = Criteria.where(NotificationEventLogKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NotificationEventLogKeys.pipelineEventType)
                            .is(pipelineEventType);
    Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, NotificationEventLogKeys.createdAt)).limit(1);
    return Optional.ofNullable(mongoTemplate.findOne(query, NotificationEventLog.class));
  }
}
