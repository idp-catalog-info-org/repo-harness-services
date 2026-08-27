/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executiongitmetadata;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity.ExecutionGitMetadataReconciliationEntityKeys;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class ExecutionGitMetadataReconciliationEntityRepositoryCustomImpl
    implements ExecutionGitMetadataReconciliationEntityRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Inject
  public ExecutionGitMetadataReconciliationEntityRepositoryCustomImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public ExecutionGitMetadataReconciliationEntity update(String uuid, Update updateOps) {
    Query query = new Query(Criteria.where(ExecutionGitMetadataReconciliationEntityKeys.uuid).is(uuid));
    updateOps.set(ExecutionGitMetadataReconciliationEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return mongoTemplate.findAndModify(query, updateOps, FindAndModifyOptions.options().returnNew(true),
        ExecutionGitMetadataReconciliationEntity.class);
  }
}
