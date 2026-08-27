/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.blockexecution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.execution.BlockExecutionMetadata;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class BlockExecutionMetadataCustomRepositoryImpl implements BlockExecutionMetadataCustomRepository {
  private final MongoTemplate secondaryMongoTemplate;
  private final MongoTemplate mongoTemplate;
  @Inject
  public BlockExecutionMetadataCustomRepositoryImpl(
      SecondaryMongoTemplateHolder secondaryMongoTemplateHolder, MongoTemplate mongoTemplate) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public List<BlockExecutionMetadata> findAll(String accountId) {
    Criteria criteria = Criteria.where(BlockExecutionMetadata.BlockExecutionKeys.accountId).is(accountId);
    Query query = new Query(criteria);
    return secondaryMongoTemplate.find(query, BlockExecutionMetadata.class);
  }

  @Override
  public boolean existsByAccountId(String accountId) {
    Criteria criteria = Criteria.where(BlockExecutionMetadata.BlockExecutionKeys.accountId).is(accountId);
    Query query = new Query(criteria);
    return secondaryMongoTemplate.exists(query, BlockExecutionMetadata.class);
  }

  @Override
  public DeleteResult delete(String pipelineIdentifier, String parentUniqueId) {
    Criteria criteria = Criteria.where(BlockExecutionMetadata.BlockExecutionKeys.parentUniqueId).is(parentUniqueId);
    if (EmptyPredicate.isNotEmpty(pipelineIdentifier)) {
      criteria.and(BlockExecutionMetadata.BlockExecutionKeys.pipelineId).is(pipelineIdentifier);
    }
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, BlockExecutionMetadata.class);
  }
}
