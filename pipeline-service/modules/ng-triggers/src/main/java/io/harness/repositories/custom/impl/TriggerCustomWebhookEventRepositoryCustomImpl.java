/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.custom.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent.TriggerCustomWebhookEventsKeys;
import io.harness.repositories.custom.TriggerCustomWebhookEventRepositoryCustom;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerCustomWebhookEventRepositoryCustomImpl implements TriggerCustomWebhookEventRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public TriggerCustomWebhookEvent update(Criteria criteria, Integer attemptCount, String status) {
    Query query = new Query(criteria);
    Update update = new Update();
    if (attemptCount != null) {
      update.set(TriggerCustomWebhookEventsKeys.attemptCount, attemptCount);
    }
    update.set(TriggerCustomWebhookEventsKeys.processingStatus, status);
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true), TriggerCustomWebhookEvent.class);
  }

  @Override
  public TriggerCustomWebhookEvent get(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, TriggerCustomWebhookEvent.class);
  }

  @Override
  public long getCount(Criteria criteria) {
    return mongoTemplate.count(new Query(criteria), TriggerCustomWebhookEvent.class);
  }
}
