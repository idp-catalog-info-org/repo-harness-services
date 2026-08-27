/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata.CIExecutionMetadataKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class CIExecutionRepositoryCustomImpl implements CIExecutionRepositoryCustom {
  MongoTemplate mongoTemplate;

  @Override
  public CIExecutionMetadata getExecutionMetadata(String accountID, String runtimeId) {
    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId);
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, CIExecutionMetadata.class);
  }

  @Override
  public CIExecutionMetadata updateExecutionStatus(String accountID, String runtimeId, String status) {
    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.status, status);
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(false), CIExecutionMetadata.class);
  }

  @Override
  public CIExecutionMetadata updateLastProcessedTime(String accountID, String runtimeId, Long lastProcessedTime) {
    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.capacityTaskProcessedTime, lastProcessedTime);
    update.set(CIExecutionMetadataKeys.capacityTaskInProgress, true);
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(false), CIExecutionMetadata.class);
  }

  @Override
  public CIExecutionMetadata updateCapacityTaskInProgress(
      String accountID, String runtimeId, boolean capacityTaskInProgress) {
    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.capacityTaskInProgress, capacityTaskInProgress);
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(false), CIExecutionMetadata.class);
  }

  @Override
  public void updateQueueId(
      String accountID, String runtimeId, String queueId, String queueTopic, String queueSubTopic) {
    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.queueId, queueId);
    update.set(CIExecutionMetadataKeys.queueTopic, queueTopic);
    update.set(CIExecutionMetadataKeys.queueSubtopic, queueSubTopic);
    mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(false), CIExecutionMetadata.class);
  }

  @Override
  public boolean tryAcquireCapacityTaskLock(
      String accountID, String runtimeId, Long currentTimeMillis, Long minProcessingWaitTime) {
    // ATOMIC: Only acquire lock if:
    // 1. capacityTaskInProgress is false/null AND
    // 2. lastProcessedTime is null OR older than wait threshold

    Long thresholdTime = currentTimeMillis - minProcessingWaitTime;

    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId)
                            .and(CIExecutionMetadataKeys.capacityTaskInProgress)
                            .ne(true) // Not currently in progress
                            .orOperator(
                                // Either lastProcessedTime is null (never processed)
                                Criteria.where(CIExecutionMetadataKeys.capacityTaskProcessedTime).is(null),
                                // OR lastProcessedTime is older than threshold
                                Criteria.where(CIExecutionMetadataKeys.capacityTaskProcessedTime).lt(thresholdTime));

    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.capacityTaskInProgress, true);
    update.set(CIExecutionMetadataKeys.capacityTaskProcessedTime, currentTimeMillis);

    // findAndModify is ATOMIC - only one thread will succeed if both conditions met
    CIExecutionMetadata result = mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(false), CIExecutionMetadata.class);

    // Return true if we successfully acquired the lock
    return result != null;
  }

  @Override
  public boolean tryAcquireConcurrencyQueueMessageProcessorLock(String accountID, String runtimeId) {
    // ATOMIC: Only acquire message processor lock if concurrencyQueueMessageProcessedTime is null OR older than 1
    // minute (ensures single-threaded processing with timeout)
    long currentTimeMillis = System.currentTimeMillis();
    long minProcessingWaitTime = 60000L; // 1 minute in milliseconds
    long thresholdTime = currentTimeMillis - minProcessingWaitTime;

    Criteria criteria =
        Criteria.where(CIExecutionMetadataKeys.accountId)
            .is(accountID)
            .and(CIExecutionMetadataKeys.stageExecutionId)
            .is(runtimeId)
            .orOperator(
                // Either concurrencyQueueMessageProcessedTime is null (never processed)
                Criteria.where(CIExecutionMetadataKeys.concurrencyQueueProcessedTime).is(null),
                // OR concurrencyQueueMessageProcessedTime is older than threshold (1 minute)
                Criteria.where(CIExecutionMetadataKeys.concurrencyQueueProcessedTime).lt(thresholdTime));

    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.concurrencyQueueProcessedTime, currentTimeMillis);

    // findAndModify is ATOMIC - only one thread will succeed
    CIExecutionMetadata result = mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(false), CIExecutionMetadata.class);

    // Return true if we successfully acquired the lock
    return result != null;
  }
}
