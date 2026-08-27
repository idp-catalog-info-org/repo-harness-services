/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.dataretention;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.ExecutionRetentionMetadata.ExecutionRetentionMetadataKeys;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class ExecutionRetentionMetadataRepositoryCustomImpl implements ExecutionRetentionMetadataRepositoryCustom {
  private final MongoTemplate secondaryMongoTemplate;
  private final MongoTemplate mongoTemplate;

  @Inject
  public ExecutionRetentionMetadataRepositoryCustomImpl(
      MongoTemplate mongoTemplate, SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  @Override
  public ExecutionRetentionMetadata fetchFromSecondary(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.findOne(query, ExecutionRetentionMetadata.class);
  }

  @Override
  public List<ExecutionRetentionMetadata> fetchAllFromSecondary(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.find(query, ExecutionRetentionMetadata.class);
  }

  @Override
  public List<ExecutionRetentionMetadata> fetchAllFromSecondary(Query query) {
    if (query.getLimit() > 1000) {
      log.warn("Limit for getting ExecutionRetentionMetadata can not exceed 1000");
      query.limit(1000);
    }
    return secondaryMongoTemplate.find(query, ExecutionRetentionMetadata.class);
  }

  /**
   * This method is used while updating execution metadata. This guarantees that we update the value, if the metadata
   * exists for an account else, it creates a new metadata entity with the new values.
   */
  @Override
  public ExecutionRetentionMetadata upsert(String planExecutionId, Update updateOps) {
    Query query = new Query(Criteria.where(ExecutionRetentionMetadataKeys.planExecutionId).is(planExecutionId));
    updateOps.setOnInsert(ExecutionRetentionMetadataKeys.createdAt, System.currentTimeMillis());
    updateOps.set(ExecutionRetentionMetadataKeys.lastUpdatedAt, System.currentTimeMillis());
    return mongoTemplate.findAndModify(query, updateOps, FindAndModifyOptions.options().returnNew(true).upsert(true),
        ExecutionRetentionMetadata.class);
  }

  /**
   * This method is used while updating execution metadata. This should only be called when the metadata already exists
   */
  @Override
  public ExecutionRetentionMetadata update(String uuid, Update updateOps) {
    Query query = new Query(Criteria.where(ExecutionRetentionMetadataKeys.uuid).is(uuid));
    updateOps.set(ExecutionRetentionMetadataKeys.lastUpdatedAt, System.currentTimeMillis());
    return mongoTemplate.findAndModify(
        query, updateOps, FindAndModifyOptions.options().returnNew(true), ExecutionRetentionMetadata.class);
  }

  @Override
  public Stream<ExecutionRetentionMetadata> streamFromSecondary(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.stream(query, ExecutionRetentionMetadata.class);
  }

  @Override
  public Stream<ExecutionRetentionMetadata> streamFromSecondary(Query query) {
    return secondaryMongoTemplate.stream(query, ExecutionRetentionMetadata.class);
  }

  @Override
  public List<String> getAllUniqueAccountIdsFromSecondary(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.findDistinct(
        query, ExecutionRetentionMetadataKeys.accountId, ExecutionRetentionMetadata.class, String.class);
  }

  @Override
  public DeleteResult delete(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, ExecutionRetentionMetadata.class);
  }

  @Override
  public ExecutionRetentionMetadata update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return mongoTemplate.findAndModify(query, update, ExecutionRetentionMetadata.class);
  }
}
