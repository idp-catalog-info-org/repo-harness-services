/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.steps.upload.RuntimeFileInputData;
import io.harness.steps.upload.RuntimeFileInputData.RuntimeFileInputDataKeys;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessRepo
public class RuntimeFileInputDataCustomRepositoryImpl implements RuntimeFileInputDataCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Autowired
  public RuntimeFileInputDataCustomRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public RuntimeFileInputData update(Query query, Update updateOps) {
    updateOps.set(RuntimeFileInputDataKeys.lastModifiedAt, System.currentTimeMillis());
    return mongoTemplate.findAndModify(
        query, updateOps, new FindAndModifyOptions().returnNew(true), RuntimeFileInputData.class);
  }

  @Override
  public List<RuntimeFileInputData> find(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, RuntimeFileInputData.class);
  }

  @Override
  public RuntimeFileInputData upsert(Query query, Update update) {
    long currentTimeInMilliseconds = System.currentTimeMillis();
    update.set(RuntimeFileInputDataKeys.lastModifiedAt, currentTimeInMilliseconds);
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(true), RuntimeFileInputData.class);
  }

  @Override
  public Long count(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.count(query, RuntimeFileInputData.class);
  }
}
