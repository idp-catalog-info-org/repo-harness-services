/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.inputset;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;

import com.google.inject.Inject;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class InputSetEntityReadHelper {
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public InputSetEntityReadHelper(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  public List<InputSetEntity> findAllFromSecondaryDB(
      Criteria criteria, List<String> fieldsToBeExcluded, Pageable pageable) {
    Query query = new Query(criteria);
    if (isNotEmpty(fieldsToBeExcluded)) {
      fieldsToBeExcluded.forEach(field -> query.fields().exclude(field));
    }
    return secondaryMongoTemplate.find(query.with(pageable), InputSetEntity.class);
  }

  public List<InputSetEntity> findAllFromSecondaryDB(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.find(query, InputSetEntity.class);
  }

  public <O> AggregationResults<O> aggregate(Aggregation aggregation, Class<O> outputType) {
    return secondaryMongoTemplate.aggregate(aggregation, InputSetEntity.class, outputType);
  }
}
