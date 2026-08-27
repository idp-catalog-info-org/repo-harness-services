/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
public class GraphUpdateInfoRepositoryCustomImpl implements GraphUpdateInfoRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;
  private static final int DEFAULT_BATCH_SIZE = 100;

  @Inject
  public GraphUpdateInfoRepositoryCustomImpl(
      MongoTemplate mongoTemplate, SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }
  @Override
  public GraphUpdateInfo update(Query query, Update update) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed updating GraphUpdateInfo; attempt: {}",
        "[Failed]: Failed updating PipelineExecutionSummary; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), GraphUpdateInfo.class));
  }

  @Override
  public Stream<GraphUpdateInfo> findGraphUpdateInfoNotProcessedInGraph(Query query) {
    query.cursorBatchSize(DEFAULT_BATCH_SIZE);
    return mongoTemplate.stream(query, GraphUpdateInfo.class);
  }

  @Override
  public Stream<GraphUpdateInfo> findGraphUpdateInfoNotProcessedInGraphFromSecondary(Query query) {
    query.cursorBatchSize(DEFAULT_BATCH_SIZE);
    return secondaryMongoTemplate.stream(query, GraphUpdateInfo.class);
  }

  @Override
  public boolean checkIfGraphUpdateInfoNotProcessedInGraph(Query query) {
    return mongoTemplate.exists(query, GraphUpdateInfo.class);
  }

  @Override
  public void upsert(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    mongoTemplate.upsert(query, update, GraphUpdateInfo.class);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }
}
