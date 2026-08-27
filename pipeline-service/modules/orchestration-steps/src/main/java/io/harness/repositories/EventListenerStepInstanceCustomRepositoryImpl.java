/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance.EventListenerStepInstanceKeys;

import java.util.Iterator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
@HarnessRepo
public class EventListenerStepInstanceCustomRepositoryImpl implements EventListenerStepInstanceCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Autowired
  public EventListenerStepInstanceCustomRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public EventListenerStepInstance updateFirst(Query query, Update update) {
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true), EventListenerStepInstance.class);
  }

  @Override
  public Iterator<EventListenerStepInstance> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    query.fields().include(EventListenerStepInstanceKeys.id, EventListenerStepInstanceKeys.webhookIdentifier,
        EventListenerStepInstanceKeys.successCriteria, EventListenerStepInstanceKeys.failureCriteria,
        EventListenerStepInstanceKeys.ambiance);
    Stream<EventListenerStepInstance> eventListenerStepInstanceStream =
        mongoTemplate.stream(query, EventListenerStepInstance.class);
    return eventListenerStepInstanceStream.iterator();
  }
}
