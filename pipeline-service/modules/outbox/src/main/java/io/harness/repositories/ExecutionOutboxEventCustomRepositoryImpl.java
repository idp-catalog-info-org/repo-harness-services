/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ExecutionOutboxEvent;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PIPELINE)
@HarnessRepo
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ExecutionOutboxEventCustomRepositoryImpl implements ExecutionOutboxEventCustomRepository {
  private final MongoTemplate mongoTemplate;

  public List<ExecutionOutboxEvent> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    return mongoTemplate.find(query, ExecutionOutboxEvent.class);
  }

  @Override
  public long count(Criteria criteria) {
    return mongoTemplate.count(new Query(criteria), ExecutionOutboxEvent.class);
  }

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, ExecutionOutboxEvent.class, classToFillResultIn);
  }
}