/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.engine.executions.plan.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.plan.service.PlanCreationQueueRequestService;
import io.harness.execution.PlanCreationQueueRequest;
import io.harness.execution.PlanCreationQueueRequest.PlanCreationQueueRequestKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PlanCreationQueueRequestServiceImpl implements PlanCreationQueueRequestService {
  @Inject private MongoTemplate mongoTemplate;
  @Override
  public PlanCreationQueueRequest save(PlanCreationQueueRequest planCreationQueueRequest) {
    return mongoTemplate.save(planCreationQueueRequest);
  }

  @Override
  public PlanCreationQueueRequest get(String planExecutionId) {
    Criteria criteria = Criteria.where(PlanCreationQueueRequestKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, PlanCreationQueueRequest.class);
  }

  @Override
  public void updateTTL(String planExecutionId) {
    Criteria criteria = Criteria.where(PlanCreationQueueRequestKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    Update ops = new Update();
    Date ttlExpiryDate = Date.from(OffsetDateTime.now().plusDays(1).toInstant());
    ops.set(PlanCreationQueueRequestKeys.validUntil, ttlExpiryDate);
    mongoTemplate.updateMulti(query, ops, PlanCreationQueueRequest.class);
  }
}