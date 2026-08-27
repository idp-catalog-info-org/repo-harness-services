/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.response;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.mongo.PipelineBucket;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.springdata.BudgetedQuery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PipelineEntityReadHelper {
  public static final int MAX_BATCH_SIZE = 10000;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public PipelineEntityReadHelper(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  public long findCount(Query query) {
    // Unbounded count over the whole match set: tag SLOW so it isn't held to the FAST budget.
    return secondaryMongoTemplate.count(
        BudgetedQuery.withBudget(Query.of(query).limit(-1).skip(-1), PipelineBucket.SLOW), PipelineEntity.class);
  }

  public List<String> findAllIdentifiers(Criteria criteria) {
    // Batch scan up to MAX_BATCH_SIZE docs: tag SLOW.
    List<PipelineEntity> pipelineEntities = secondaryMongoTemplate.find(
        BudgetedQuery.withBudget(new Query(criteria).limit(MAX_BATCH_SIZE), PipelineBucket.SLOW), PipelineEntity.class);
    return pipelineEntities.stream().map(PipelineEntity::getIdentifier).collect(Collectors.toList());
  }

  public Stream<PipelineEntity> findAllPipelines(Criteria criteria, List<String> fieldsToBeExcluded) {
    Query query = new Query(criteria);
    if (isNotEmpty(fieldsToBeExcluded)) {
      fieldsToBeExcluded.forEach(field -> query.fields().exclude(field));
    }
    // Full cursor scan: tag SLOW for the bulk budget.
    return secondaryMongoTemplate.stream(BudgetedQuery.withBudget(query, PipelineBucket.SLOW), PipelineEntity.class);
  }

  public List<PipelineEntity> find(Criteria criteria) {
    // No limit: an unbounded scan, tag SLOW.
    return secondaryMongoTemplate.find(
        BudgetedQuery.withBudget(new Query(criteria), PipelineBucket.SLOW), PipelineEntity.class);
  }

  public <O> AggregationResults<O> aggregate(Aggregation aggregation, Class<O> outputType) {
    return secondaryMongoTemplate.aggregate(aggregation, PipelineEntity.class, outputType);
  }
}
