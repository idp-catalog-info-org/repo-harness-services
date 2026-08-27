/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.conversion;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionJobEntity.ConversionJobEntityKeys;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Custom repository implementation for ConversionJobEntity.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ConversionJobRepositoryCustomImpl implements ConversionJobRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public ConversionJobEntity update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), ConversionJobEntity.class));
  }

  @Override
  public Optional<ConversionJobEntity> findOne(Criteria criteria, Sort sort) {
    Query query = new Query(criteria).with(sort).limit(1);
    ConversionJobEntity job = mongoTemplate.findOne(query, ConversionJobEntity.class);
    return Optional.ofNullable(job);
  }

  @Override
  public List<ConversionJobEntity> findAll(Criteria criteria, Sort sort) {
    Query query = new Query(criteria).with(sort);
    return mongoTemplate.find(query, ConversionJobEntity.class);
  }

  @Override
  public Optional<ConversionJobEntity> findLatestByAccountAndStatus(String accountId, ConversionStatus status) {
    Criteria criteria =
        Criteria.where(ConversionJobEntityKeys.accountId).is(accountId).and(ConversionJobEntityKeys.status).is(status);
    Query query = new Query(criteria).with(Sort.by(Direction.DESC, ConversionJobEntityKeys.startTs)).limit(1);
    ConversionJobEntity job = mongoTemplate.findOne(query, ConversionJobEntity.class);
    return Optional.ofNullable(job);
  }

  @Override
  public List<ConversionJobEntity> findByAccount(String accountId, int limit, int offset) {
    Criteria criteria = Criteria.where(ConversionJobEntityKeys.accountId).is(accountId);
    Query query = new Query(criteria)
                      .with(Sort.by(Direction.DESC, ConversionJobEntityKeys.startTs))
                      .with(PageRequest.of(offset / limit, limit));
    return mongoTemplate.find(query, ConversionJobEntity.class);
  }

  private RetryPolicy<Object> getRetryPolicy() {
    return PersistenceUtils.getRetryPolicy("[Retrying]: Failed updating ConversionJobEntity; attempt: {}",
        "[Failed]: Failed updating ConversionJobEntity; attempt: {}");
  }
}
