/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence.PipelineBranchSequenceKeys;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Implementation of custom repository operations for PipelineBranchSequence.
 *
 * <p>Uses MongoDB atomic operations (findAndModify with upsert) to ensure
 * thread-safe counter increments without race conditions.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CI)
public class PipelineBranchSequenceRepositoryCustomImpl implements PipelineBranchSequenceRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PipelineBranchSequence incrementAndGet(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String normalizedRepoUrl, String branch,
      @Nullable String parentUniqueId) {
    Criteria criteria = buildCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, normalizedRepoUrl, branch);

    Update update = new Update();
    // Increment the sequence counter
    update.inc(PipelineBranchSequenceKeys.sequenceId, 1);
    // Set lastUpdatedAt on every update
    update.set(PipelineBranchSequenceKeys.lastUpdatedAt, System.currentTimeMillis());
    // Set createdAt only on insert (when document is first created)
    update.setOnInsert(PipelineBranchSequenceKeys.createdAt, System.currentTimeMillis());
    // Set uniqueId on insert (UniqueIdAware pattern)
    update.setOnInsert(PipelineBranchSequenceKeys.uniqueId, UUIDGenerator.generateUuid());
    // Set parentUniqueId on insert if provided (UniqueIdAware pattern)
    if (isNotEmpty(parentUniqueId)) {
      update.setOnInsert(PipelineBranchSequenceKeys.parentUniqueId, parentUniqueId);
    }
    // Set all the identifying fields on insert
    update.setOnInsert(PipelineBranchSequenceKeys.accountIdentifier, accountIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.orgIdentifier, orgIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.projectIdentifier, projectIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.pipelineIdentifier, pipelineIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.normalizedRepoUrl, normalizedRepoUrl);
    update.setOnInsert(PipelineBranchSequenceKeys.branch, branch);

    FindAndModifyOptions options = new FindAndModifyOptions()
                                       .returnNew(true) // Return the updated document with new sequenceId
                                       .upsert(true); // Create if doesn't exist

    RetryPolicy<Object> retryPolicy =
        getRetryPolicyWithDuplicateKey("[Retrying]: Failed incrementing branch sequence; attempt: {}",
            "[Failed]: Failed incrementing branch sequence; attempt: {}");

    PipelineBranchSequence result =
        Failsafe.with(retryPolicy)
            .get(() -> mongoTemplate.findAndModify(new Query(criteria), update, options, PipelineBranchSequence.class));

    log.info("Incremented branch sequence for pipeline={}, repo={}, branch={} to sequenceId={}", pipelineIdentifier,
        normalizedRepoUrl, branch, result != null ? result.getSequenceId() : "null");

    return result;
  }

  @Override
  public Optional<PipelineBranchSequence> getBranchSequence(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String normalizedRepoUrl, String branch) {
    Criteria criteria = buildCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, normalizedRepoUrl, branch);

    PipelineBranchSequence result = mongoTemplate.findOne(new Query(criteria), PipelineBranchSequence.class);
    return Optional.ofNullable(result);
  }

  @Override
  public long deleteAllForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Criteria criteria = Criteria.where(PipelineBranchSequenceKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(PipelineBranchSequenceKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineBranchSequenceKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineBranchSequenceKeys.pipelineIdentifier)
                            .is(pipelineIdentifier);

    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting branch sequences for pipeline; attempt: {}",
            "[Failed]: Failed deleting branch sequences for pipeline; attempt: {}");

    DeleteResult deleteResult =
        Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(new Query(criteria), PipelineBranchSequence.class));

    long deletedCount = deleteResult.getDeletedCount();
    log.info("Deleted {} branch sequence records for pipeline={}", deletedCount, pipelineIdentifier);

    return deletedCount;
  }

  @Override
  public List<PipelineBranchSequence> getAllForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Criteria criteria = Criteria.where(PipelineBranchSequenceKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(PipelineBranchSequenceKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineBranchSequenceKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineBranchSequenceKeys.pipelineIdentifier)
                            .is(pipelineIdentifier);

    return mongoTemplate.find(new Query(criteria), PipelineBranchSequence.class);
  }

  @Override
  public boolean deleteBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String normalizedRepoUrl, String branch) {
    Criteria criteria = buildCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, normalizedRepoUrl, branch);

    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting branch sequence; attempt: {}",
            "[Failed]: Failed deleting branch sequence; attempt: {}");

    DeleteResult deleteResult =
        Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(new Query(criteria), PipelineBranchSequence.class));

    boolean deleted = deleteResult.getDeletedCount() > 0;
    log.info("Deleted branch sequence for pipeline={}, repo={}, branch={}: {}", pipelineIdentifier, normalizedRepoUrl,
        branch, deleted);

    return deleted;
  }

  @Override
  public PipelineBranchSequence setSequenceId(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String normalizedRepoUrl, String branch, long sequenceId) {
    Criteria criteria = buildCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, normalizedRepoUrl, branch);

    Update update = new Update();
    // Set the sequence counter to the specified value
    update.set(PipelineBranchSequenceKeys.sequenceId, sequenceId);
    // Set lastUpdatedAt on every update
    update.set(PipelineBranchSequenceKeys.lastUpdatedAt, System.currentTimeMillis());
    // Set createdAt only on insert (when document is first created)
    update.setOnInsert(PipelineBranchSequenceKeys.createdAt, System.currentTimeMillis());
    // Set uniqueId on insert (UniqueIdAware pattern)
    update.setOnInsert(PipelineBranchSequenceKeys.uniqueId, UUIDGenerator.generateUuid());
    // Set all the identifying fields on insert
    update.setOnInsert(PipelineBranchSequenceKeys.accountIdentifier, accountIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.orgIdentifier, orgIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.projectIdentifier, projectIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.pipelineIdentifier, pipelineIdentifier);
    update.setOnInsert(PipelineBranchSequenceKeys.normalizedRepoUrl, normalizedRepoUrl);
    update.setOnInsert(PipelineBranchSequenceKeys.branch, branch);

    FindAndModifyOptions options = new FindAndModifyOptions()
                                       .returnNew(true) // Return the updated document with new sequenceId
                                       .upsert(true); // Create if doesn't exist

    RetryPolicy<Object> retryPolicy =
        getRetryPolicyWithDuplicateKey("[Retrying]: Failed setting branch sequence; attempt: {}",
            "[Failed]: Failed setting branch sequence; attempt: {}");

    PipelineBranchSequence result =
        Failsafe.with(retryPolicy)
            .get(() -> mongoTemplate.findAndModify(new Query(criteria), update, options, PipelineBranchSequence.class));

    log.info("Set branch sequence for pipeline={}, repo={}, branch={} to sequenceId={}", pipelineIdentifier,
        normalizedRepoUrl, branch, result != null ? result.getSequenceId() : "null");

    return result;
  }

  private static RetryPolicy<Object> getRetryPolicyWithDuplicateKey(
      String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(
        failedAttemptMessage, failureMessage, ex -> ex instanceof DuplicateKeyException);
  }

  /**
   * Builds the criteria for querying/updating a specific branch sequence record.
   */
  private Criteria buildCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String normalizedRepoUrl, String branch) {
    return Criteria.where(PipelineBranchSequenceKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(PipelineBranchSequenceKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(PipelineBranchSequenceKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(PipelineBranchSequenceKeys.pipelineIdentifier)
        .is(pipelineIdentifier)
        .and(PipelineBranchSequenceKeys.normalizedRepoUrl)
        .is(normalizedRepoUrl)
        .and(PipelineBranchSequenceKeys.branch)
        .is(branch);
  }
}
