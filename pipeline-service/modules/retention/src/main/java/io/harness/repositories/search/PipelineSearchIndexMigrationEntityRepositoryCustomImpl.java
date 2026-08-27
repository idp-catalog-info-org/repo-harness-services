/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.search;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity.PipelineSearchIndexMigrationEntityKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PipelineSearchIndexMigrationEntityRepositoryCustomImpl
    implements PipelineSearchIndexMigrationEntityRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public PipelineSearchIndexMigrationEntityRepositoryCustomImpl(
      SecondaryMongoTemplateHolder secondaryMongoTemplateHolder, MongoTemplate mongoTemplate) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public PipelineSearchIndexMigrationEntity update(String uuid, Update updateOps) {
    Query query = new Query(Criteria.where(PipelineSearchIndexMigrationEntityKeys.uuid).is(uuid));
    updateOps.set(PipelineSearchIndexMigrationEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return mongoTemplate.findAndModify(
        query, updateOps, FindAndModifyOptions.options().returnNew(true), PipelineSearchIndexMigrationEntity.class);
  }

  @Override
  public PipelineSearchIndexMigrationEntity findByAccountIdentifier(String accountId) {
    Criteria criteria = Criteria.where(PipelineSearchIndexMigrationEntityKeys.accountIdentifier).is(accountId);
    return secondaryMongoTemplate.findOne(new Query(criteria), PipelineSearchIndexMigrationEntity.class);
  }
}
