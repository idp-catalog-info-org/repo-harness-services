/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.reconciliation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity.ExecutionRetentionReconciliationEntityKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class ExecutionRetentionReconciliationEntityRepositoryCustomImpl
    implements ExecutionRetentionReconciliationEntityRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public ExecutionRetentionReconciliationEntityRepositoryCustomImpl(
      MongoTemplate mongoTemplate, SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  @Override
  public ExecutionRetentionReconciliationEntity update(String uuid, Update updateOps) {
    Query query = new Query(Criteria.where(ExecutionRetentionReconciliationEntityKeys.uuid).is(uuid));
    updateOps.set(ExecutionRetentionReconciliationEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return mongoTemplate.findAndModify(
        query, updateOps, FindAndModifyOptions.options().returnNew(true), ExecutionRetentionReconciliationEntity.class);
  }

  @Override
  public List<ExecutionRetentionReconciliationEntity> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.find(query, ExecutionRetentionReconciliationEntity.class);
  }
}
