/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executiongitmetadata;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.execution.gitmetadata.PipelineExecutionGitMetadata;
import io.harness.execution.gitmetadata.PipelineExecutionGitMetadata.PipelineExecutionGitMetadataKeys;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
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
public class PipelineExecutionGitMetadataRepositoryCustomImpl implements PipelineExecutionGitMetadataRepositoryCustom {
  public static final int MAX_BATCH_SIZE = 1000;
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public PipelineExecutionGitMetadataRepositoryCustomImpl(
      SecondaryMongoTemplateHolder secondaryMongoTemplateHolder, MongoTemplate mongoTemplate) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public PipelineExecutionGitMetadata upsert(
      ScopeInfo scopeInfo, String pipelineIdentifier, String repoName, String branch) {
    String accountIdentifier = scopeInfo.getAccountIdentifier();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();
    String parentUniqueId = scopeInfo.getUniqueId();
    Criteria criteria = Criteria.where(PipelineExecutionGitMetadataKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(PipelineExecutionGitMetadataKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(PipelineExecutionGitMetadataKeys.pipelineIdentifier)
                            .is(pipelineIdentifier)
                            .and(PipelineExecutionGitMetadataKeys.repoName)
                            .is(repoName);
    long currentTime = System.currentTimeMillis();
    Update update = new Update()
                        .setOnInsert(PipelineExecutionGitMetadataKeys.accountIdentifier, accountIdentifier)
                        .setOnInsert(PipelineExecutionGitMetadataKeys.orgIdentifier, orgIdentifier)
                        .setOnInsert(PipelineExecutionGitMetadataKeys.uniqueId, generateUuid())
                        .setOnInsert(PipelineExecutionGitMetadataKeys.parentUniqueId, parentUniqueId)
                        .setOnInsert(PipelineExecutionGitMetadataKeys.projectIdentifier, projectIdentifier)
                        .setOnInsert(PipelineExecutionGitMetadataKeys.pipelineIdentifier, pipelineIdentifier)
                        .setOnInsert(PipelineExecutionGitMetadataKeys.repoName, repoName)
                        .setOnInsert(PipelineExecutionGitMetadataKeys.createdAt, currentTime)
                        .addToSet(PipelineExecutionGitMetadataKeys.branch, branch)
                        .set(PipelineExecutionGitMetadataKeys.lastUpdatedAt, currentTime);

    FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
    return mongoTemplate.findAndModify(new Query(criteria), update, options, PipelineExecutionGitMetadata.class);
  }

  @Override
  public List<String> findUniqueListOfRepositories(ScopeInfo scopeInfo, String pipelineIdentifier) {
    Criteria criteria = Criteria.where(PipelineExecutionGitMetadataKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(PipelineExecutionGitMetadataKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId());
    if (isNotEmpty(pipelineIdentifier)) {
      criteria.and(PipelineExecutionGitMetadataKeys.pipelineIdentifier).is(pipelineIdentifier);
    }

    Query query = new Query(criteria);
    query.limit(MAX_BATCH_SIZE);
    return secondaryMongoTemplate.findDistinct(
        query, PipelineExecutionGitMetadataKeys.repoName, PipelineExecutionGitMetadata.class, String.class);
  }

  @Override
  public List<String> findUniqueListOfBranches(ScopeInfo scopeInfo, String pipelineIdentifier, String repoName) {
    Criteria criteria = Criteria.where(PipelineExecutionGitMetadataKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(PipelineExecutionGitMetadataKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId());
    if (isNotEmpty(pipelineIdentifier)) {
      criteria.and(PipelineExecutionGitMetadataKeys.pipelineIdentifier).is(pipelineIdentifier);
    }

    if (isNotEmpty(repoName)) {
      criteria.and(PipelineExecutionGitMetadataKeys.repoName).is(repoName);
    }

    Query query = new Query(criteria);
    query.limit(MAX_BATCH_SIZE);

    return secondaryMongoTemplate.findDistinct(
        query, PipelineExecutionGitMetadataKeys.branch, PipelineExecutionGitMetadata.class, String.class);
  }

  @Override
  public void deleteGitMetadataForPipeline(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String parentUniqueId) {
    Criteria criteria = Criteria.where(PipelineExecutionGitMetadataKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(PipelineExecutionGitMetadataKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(PipelineExecutionGitMetadataKeys.pipelineIdentifier)
                            .is(pipelineIdentifier);
    Query query = new Query(criteria);
    log.info("Deleting execution git metadata for pipeline: {}/{}/{}/{}", accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier);
    mongoTemplate.remove(query, PipelineExecutionGitMetadata.class);
  }
}
