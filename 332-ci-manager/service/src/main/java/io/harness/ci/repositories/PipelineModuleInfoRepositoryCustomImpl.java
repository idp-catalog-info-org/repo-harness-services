/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ci.beans.entities.PipelineModuleInfoEntity;
import io.harness.ci.beans.entities.StageModuleInfoEntity;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Implementation of custom repository operations for CIPipelineModuleInfoEntity.
 * Handles atomic updates to prevent race conditions during concurrent stage updates.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineModuleInfoRepositoryCustomImpl implements PipelineModuleInfoRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PipelineModuleInfoEntity addStageModuleInfo(String accountId, String orgId, String projectId,
      String pipelineId, String planExecutionId, String parentUniqueId, StageModuleInfoEntity stageInfo) {
    long now = System.currentTimeMillis();
    Query query = new Query(Criteria.where("planExecutionId").is(planExecutionId));

    Update addToSetUpdate = new Update()
                                .addToSet("stageModuleInfoList", stageInfo)
                                .set("updatedAt", now)
                                .setOnInsert("accountIdentifier", accountId)
                                .setOnInsert("orgIdentifier", orgId)
                                .setOnInsert("projIdentifier", projectId)
                                .setOnInsert("pipelineIdentifier", pipelineId)
                                .setOnInsert("planExecutionId", planExecutionId)
                                .setOnInsert("parentUniqueId", parentUniqueId)
                                .setOnInsert("createdAt", now);

    FindAndModifyOptions options = new FindAndModifyOptions().upsert(true).returnNew(true);
    return mongoTemplate.findAndModify(query, addToSetUpdate, options, PipelineModuleInfoEntity.class);
  }
}
