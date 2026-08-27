/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class AggregationRuleRepositoryCustomImpl implements AggregationRuleRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Page<AggregationRuleEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<AggregationRuleEntity> aggregationRuleEntityList = mongoTemplate.find(query, AggregationRuleEntity.class);
    return PageableExecutionUtils.getPage(aggregationRuleEntityList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), AggregationRuleEntity.class));
  }

  @Override
  public Page<AggregationRuleEntity> getAggregationRules(
      String accountIdentifier, Pageable pageable, Set<String> permittedIdentifiers) {
    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();
    criteria.add(Criteria.where(AggregationRuleEntity.AggregationRuleKeys.accountIdentifier).is(accountIdentifier));
    if (isEmpty(permittedIdentifiers)) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }
    criteria.add(Criteria.where(AggregationRuleEntity.AggregationRuleKeys.identifier).in(permittedIdentifiers));
    query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    long totalRecords = mongoTemplate.count(query, AggregationRuleEntity.class);
    query.with(pageable);
    List<AggregationRuleEntity> aggregationRuleEntityList = mongoTemplate.find(query, AggregationRuleEntity.class);
    return new PageImpl<>(aggregationRuleEntityList, pageable, totalRecords);
  }

  @Override
  public Page<AggregationRuleEntity> findByAccountIdentifierAndAggregationTypeAndFieldForAgg(String accountIdentifier,
      AggregationRuleEntity.AggregationType aggregationType, String fieldForAgg, Pageable pageable) {
    Query query = new Query();
    query.addCriteria(
        Criteria.where(AggregationRuleEntity.AggregationRuleKeys.accountIdentifier).is(accountIdentifier));
    query.addCriteria(Criteria.where(AggregationRuleEntity.AggregationRuleKeys.aggregationType).is(aggregationType));
    query.addCriteria(Criteria.where(AggregationRuleEntity.AggregationRuleKeys.fieldForAgg).is(fieldForAgg));
    long totalRecords = mongoTemplate.count(query, AggregationRuleEntity.class);
    query.with(pageable);
    List<AggregationRuleEntity> aggregationRuleEntityList = mongoTemplate.find(query, AggregationRuleEntity.class);
    return new PageImpl<>(aggregationRuleEntityList, pageable, totalRecords);
  }
}
