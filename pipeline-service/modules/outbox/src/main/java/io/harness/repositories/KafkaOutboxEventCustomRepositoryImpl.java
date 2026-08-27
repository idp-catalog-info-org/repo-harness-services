/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.KafkaOutboxEvent;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PIPELINE)
@HarnessRepo
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class KafkaOutboxEventCustomRepositoryImpl implements KafkaOutboxEventCustomRepository {
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public KafkaOutboxEventCustomRepositoryImpl(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  @Override
  public List<KafkaOutboxEvent> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    return secondaryMongoTemplate.find(query, KafkaOutboxEvent.class);
  }

  @Override
  public long count(Criteria criteria) {
    return secondaryMongoTemplate.count(new Query(criteria), KafkaOutboxEvent.class);
  }

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return secondaryMongoTemplate.aggregate(aggregation, KafkaOutboxEvent.class, classToFillResultIn);
  }
}
