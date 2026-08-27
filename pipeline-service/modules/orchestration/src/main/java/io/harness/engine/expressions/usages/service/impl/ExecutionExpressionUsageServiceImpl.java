/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.expressions.usages.service.impl;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.usages.ExecutionExpressionUsageReadHelper;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity.ExecutionExpressionUsagesEntityKeys;
import io.harness.engine.expressions.usages.service.ExecutionExpressionUsageService;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ExecutionExpressionUsageServiceImpl implements ExecutionExpressionUsageService {
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;
  private final ExecutionExpressionUsageReadHelper executionExpressionUsageReadHelper;

  @Inject
  public ExecutionExpressionUsageServiceImpl(MongoTemplate mongoTemplate,
      SecondaryMongoTemplateHolder secondaryMongoTemplateHolder,
      ExecutionExpressionUsageReadHelper executionExpressionUsageReadHelper) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.executionExpressionUsageReadHelper = executionExpressionUsageReadHelper;
  }

  @Override
  public void saveExpressions(List<ExecutionExpressionUsagesEntity> expressions) {
    if (EmptyPredicate.isNotEmpty(expressions)) {
      mongoTemplate.insertAll(expressions);
    }
  }

  @Override
  public List<ExecutionExpressionUsagesEntity> getExpressions(String planExecutionId, String nodeExecutionId) {
    if (EmptyPredicate.isEmpty(nodeExecutionId)) {
      return new LinkedList<>();
    }
    Query query = query(where(ExecutionExpressionUsagesEntityKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(ExecutionExpressionUsagesEntityKeys.nodeExecutionId)
                            .is(nodeExecutionId));
    return secondaryMongoTemplate.find(query, ExecutionExpressionUsagesEntity.class);
  }

  @Override
  public List<ExecutionExpressionUsagesEntity> getExpressionsWithProjection(
      String planExecutionId, String nodeExecutionId, Set<String> fieldsToInclude) {
    if (EmptyPredicate.isEmpty(nodeExecutionId)) {
      return new LinkedList<>();
    }
    Query query = query(where(ExecutionExpressionUsagesEntityKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(ExecutionExpressionUsagesEntityKeys.nodeExecutionId)
                            .is(nodeExecutionId));
    for (String fieldName : fieldsToInclude) {
      query.fields().include(fieldName);
    }
    List<ExecutionExpressionUsagesEntity> executionExpressionUsagesEntities = new LinkedList<>();
    try (Stream<ExecutionExpressionUsagesEntity> stream =
             executionExpressionUsageReadHelper.fetchExecutionExpressionUsagesEntity(query)) {
      Iterator<ExecutionExpressionUsagesEntity> iterator = stream.iterator();
      while (iterator.hasNext()) {
        ExecutionExpressionUsagesEntity executionExpressionUsagesEntity = iterator.next();
        executionExpressionUsagesEntities.add(executionExpressionUsagesEntity);
      }
    }
    return executionExpressionUsagesEntities;
  }
}
