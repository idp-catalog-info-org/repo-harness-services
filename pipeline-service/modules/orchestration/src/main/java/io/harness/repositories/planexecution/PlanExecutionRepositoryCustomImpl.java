/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.planexecution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.monitoring.PlanExecutionCountWithAccountResult.PlanExecutionCountWithAccountResultKeys;
import static io.harness.monitoring.PlanExecutionCountWithAccountResult.StatusCount.StatusCountKeys;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult.PlanExecutionCountWithAccountAndTriggerTypeResultKeys;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult.TriggerTypeCount.TriggerTypeCountKeys;
import io.harness.monitoring.PlanExecutionCountWithAccountResult;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
public class PlanExecutionRepositoryCustomImpl implements PlanExecutionRepositoryCustom {
  private static final int MAX_BATCH_SIZE = 1000;
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public PlanExecutionRepositoryCustomImpl(
      MongoTemplate mongoTemplate, SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  @Override
  public PlanExecution getWithProjectionsWithoutUuid(String planExecutionId, List<String> fieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : fieldNames) {
      query.fields().include(fieldName);
    }
    query.fields().exclude(PlanExecutionKeys.uuid);
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  @Override
  public PlanExecution updatePlanExecution(Query query, Update updateOps, boolean upsert) {
    return mongoTemplate.findAndModify(
        query, updateOps, new FindAndModifyOptions().upsert(upsert).returnNew(true), PlanExecution.class);
  }

  @Override
  public void multiUpdatePlanExecution(Query query, Update updateOps) {
    mongoTemplate.updateMulti(query, updateOps, PlanExecution.class);
  }
  @Override
  public PlanExecution getPlanExecutionWithProjections(String planExecutionId, List<String> excludedFieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : excludedFieldNames) {
      query.fields().exclude(fieldName);
    }
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  @Override
  public PlanExecution getPlanExecutionWithProjectionsFromAnalytics(String planExecutionId, Set<String> fieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : fieldNames) {
      query.fields().include(fieldName);
    }
    return secondaryMongoTemplate.findOne(query, PlanExecution.class);
  }

  @Override
  public PlanExecution getPlanExecutionWithProjectionsFromSecondary(String planExecutionId, Set<String> fieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : fieldNames) {
      query.fields().include(fieldName);
    }
    return secondaryMongoTemplate.findOne(query, PlanExecution.class);
  }

  @Override
  public PlanExecution getPlanExecutionWithIncludedProjections(
      String planExecutionId, List<String> includedFieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : includedFieldNames) {
      query.fields().include(fieldName);
    }
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  public Stream<PlanExecution> fetchPlanExecutionsFromAnalytics(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    validatePlanExecutionStreamQuery(query);
    return secondaryMongoTemplate.stream(query, PlanExecution.class);
  }

  @Override
  public List<PlanExecutionCountWithAccountResult> aggregateActiveExecutionsCountPerAccount() {
    Aggregation aggregation = Aggregation.newAggregation(
        Aggregation.match(Criteria.where(PlanExecutionKeys.status).in(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED)),
        Aggregation.group(PlanExecutionKeys.accountId, PlanExecutionKeys.status).count().as(StatusCountKeys.count),
        Aggregation.group(PlanExecutionCountWithAccountResultKeys.accountId)
            .push(new BasicDBObject(StatusCountKeys.status, "$_id." + StatusCountKeys.status)
                      .append(StatusCountKeys.count, "$" + StatusCountKeys.count))
            .as(PlanExecutionCountWithAccountResultKeys.statusCounts));

    return secondaryMongoTemplate.aggregate(aggregation, PlanExecution.class, PlanExecutionCountWithAccountResult.class)
        .getMappedResults();
  }

  @Override
  public List<PlanExecutionCountWithAccountAndTriggerTypeResult>
  aggregateActiveExecutionsCountPerAccountByTriggerType() {
    Aggregation aggregation = Aggregation.newAggregation(
        Aggregation.match(Criteria.where(PlanExecutionKeys.status).in(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED)),
        Aggregation
            .group(Fields.from(
                Fields.field(PlanExecutionKeys.accountId), Fields.field("triggerType", PlanExecutionKeys.triggerType)))
            .count()
            .as(TriggerTypeCountKeys.count),
        Aggregation.group(PlanExecutionCountWithAccountAndTriggerTypeResultKeys.accountId)
            .push(new BasicDBObject(TriggerTypeCountKeys.triggerType, "$_id." + TriggerTypeCountKeys.triggerType)
                      .append(TriggerTypeCountKeys.count, "$" + TriggerTypeCountKeys.count))
            .as(PlanExecutionCountWithAccountAndTriggerTypeResultKeys.triggerTypeCounts));

    return secondaryMongoTemplate
        .aggregate(aggregation, PlanExecution.class, PlanExecutionCountWithAccountAndTriggerTypeResult.class)
        .getMappedResults();
  }

  @Override
  public List<String> findAllAccountIdsWithExecutionsFromAnalytics() {
    return secondaryMongoTemplate.findDistinct(
        new Query(new Criteria()), PlanExecutionKeys.accountId, PlanExecution.class, String.class);
  }

  private void validatePlanExecutionStreamQuery(Query query) {
    if (query.getMeta().getCursorBatchSize() == null || query.getMeta().getCursorBatchSize() <= 0
        || query.getMeta().getCursorBatchSize() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          "PlanExecution query should have cursorBatch limit within max batch size- " + MAX_BATCH_SIZE);
    }
    validatePlanExecutionProjection(query);
  }

  private void validatePlanExecutionProjection(Query query) {
    if (query.getFieldsObject().isEmpty()) {
      throw new InvalidRequestException("PlanExecution list query should have projection fields");
    }
  }
}
