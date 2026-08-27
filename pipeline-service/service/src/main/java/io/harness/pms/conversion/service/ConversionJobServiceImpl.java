/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionChecksum.ConversionChecksumKeys;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionJobEntity.ConversionJobEntityKeys;
import io.harness.pms.conversion.beans.ConversionJobMetricsDTO;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.repositories.conversion.ConversionChecksumRepository;
import io.harness.repositories.conversion.ConversionJobRepository;
import io.harness.security.dto.Principal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Service implementation for managing V0 to V1 conversion jobs.
 * Jobs are processed by the ConversionJobIterator (not async executor).
 */
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ConversionJobServiceImpl implements ConversionJobService {
  private final ConversionJobRepository conversionJobRepository;
  private final ConversionChecksumRepository conversionChecksumRepository;

  @Inject
  public ConversionJobServiceImpl(
      ConversionJobRepository conversionJobRepository, ConversionChecksumRepository conversionChecksumRepository) {
    this.conversionJobRepository = conversionJobRepository;
    this.conversionChecksumRepository = conversionChecksumRepository;
  }

  @Override
  public ConversionJobEntity createJob(ConversionJobEntity jobEntity) {
    ConversionJobEntity savedJobEntity = conversionJobRepository.save(jobEntity);
    log.info("Created conversion job with uuid: {}, actionType: {}, entityType: {}, accountId: {}",
        savedJobEntity.getUuid(), savedJobEntity.getActionType(), savedJobEntity.getEntityType(),
        savedJobEntity.getAccountId());
    return savedJobEntity;
  }

  @Override
  public Optional<ConversionJobEntity> getJobByUuid(String uuid) {
    return conversionJobRepository.findById(uuid);
  }

  @Override
  public Optional<ConversionJobEntity> getJobByEntityScope(
      String accountId, String orgId, String projectId, String entityId, EntityType entityType) {
    Criteria criteria = Criteria.where(ConversionJobEntityKeys.accountId)
                            .is(accountId)
                            .and(ConversionJobEntityKeys.entityType)
                            .is(entityType)
                            .and(ConversionJobEntityKeys.entityIdentifier)
                            .is(entityId);

    // Only add orgId/projectId filters when non-null (account-scope templates have null orgId)
    if (orgId != null) {
      criteria.and(ConversionJobEntityKeys.orgId).is(orgId);
    } else {
      criteria.and(ConversionJobEntityKeys.orgId).isNull();
    }

    if (projectId != null) {
      criteria.and(ConversionJobEntityKeys.projectId).is(projectId);
    } else {
      criteria.and(ConversionJobEntityKeys.projectId).isNull();
    }

    // Only return root-level jobs (no children)
    criteria.and(ConversionJobEntityKeys.parentJobId).isNull();

    return conversionJobRepository.findOne(criteria, Sort.by(Sort.Direction.DESC, ConversionJobEntityKeys.createdAt));
  }

  @Override
  public ConversionJobEntity updateJobStatus(String uuid, ConversionStatus status, ConversionJobMetricsDTO metrics) {
    Criteria criteria = Criteria.where(ConversionJobEntityKeys.uuid).is(uuid);
    Update updateOperations = new Update()
                                  .set(ConversionJobEntityKeys.status, status)
                                  .set(ConversionJobEntityKeys.conversionMetrics, metrics);

    if (status == ConversionStatus.IN_PROGRESS) {
      updateOperations.set(ConversionJobEntityKeys.startTs, System.currentTimeMillis());
    }

    if (ConversionStatus.isFinalStatus(status)) {
      updateOperations.set(ConversionJobEntityKeys.endTs, System.currentTimeMillis());
      // Clear only the heavy YAML fields from entityMetadata, keeping lightweight routing fields
      // (storeType, repo, branch, versionLabel) intact for parent's lookupChecksum resolution
      updateOperations.unset(ConversionJobEntityKeys.entityMetadata + ".yaml");
      updateOperations.unset(ConversionJobEntityKeys.entityMetadata + ".contextPipelineYaml");
    }

    ConversionJobEntity updatedJob = conversionJobRepository.update(criteria, updateOperations);
    log.info("Updated conversion job {} to status: {}", uuid, status);
    return updatedJob;
  }

  @Override
  public ConversionJobEntity retryJob(String uuid) {
    // Re-queue failed/partial children FIRST so they are ready before the parent becomes visible to the iterator
    List<ConversionJobEntity> children = getChildJobs(uuid);
    for (ConversionJobEntity child : children) {
      if (child.getStatus() == ConversionStatus.FAILED || child.getStatus() == ConversionStatus.PARTIAL_SUCCESS) {
        retryJob(child.getUuid());
      }
    }

    Criteria criteria = Criteria.where(ConversionJobEntityKeys.uuid).is(uuid);
    Optional<ConversionJobEntity> jobOpt = conversionJobRepository.findById(uuid);

    Update updateOperations = new Update()
                                  .set(ConversionJobEntityKeys.retryCount, 0)
                                  .set(ConversionJobEntityKeys.nextIteration, System.currentTimeMillis())
                                  .unset(ConversionJobEntityKeys.endTs)
                                  .unset(ConversionJobEntityKeys.errorMessage)
                                  .unset(ConversionJobEntityKeys.lastFailureReason);

    // If the entity was already converted (yamlConverted=true), keep it IN_PROGRESS so the iterator
    // goes to handleInputSetChildrenCheck() directly instead of re-running Phase 1
    if (jobOpt.isPresent() && Boolean.TRUE.equals(jobOpt.get().getYamlConverted()) && !children.isEmpty()) {
      updateOperations.set(ConversionJobEntityKeys.status, ConversionStatus.IN_PROGRESS);
      log.info("Retried conversion job {} - kept IN_PROGRESS (yamlConverted=true, waiting for children)", uuid);
    } else {
      updateOperations.set(ConversionJobEntityKeys.status, ConversionStatus.QUEUED);
      log.info("Retried conversion job {} - reset to QUEUED", uuid);
    }

    ConversionJobEntity updatedJob = conversionJobRepository.update(criteria, updateOperations);
    return updatedJob;
  }

  @Override
  public List<ConversionJobEntity> getChildJobs(String parentJobId) {
    Criteria criteria = Criteria.where(ConversionJobEntityKeys.parentJobId).is(parentJobId);
    return conversionJobRepository.findAll(criteria, Sort.by(Sort.Direction.ASC, ConversionJobEntityKeys.createdAt));
  }

  @Override
  public void updateTriggerPrincipal(String uuid, Principal principal) {
    Criteria criteria = Criteria.where(ConversionJobEntityKeys.uuid).is(uuid);
    Update update = new Update().set(ConversionJobEntityKeys.triggerPrincipal, principal);
    conversionJobRepository.update(criteria, update);

    List<ConversionJobEntity> children = getChildJobs(uuid);
    for (ConversionJobEntity child : children) {
      updateTriggerPrincipal(child.getUuid(), principal);
    }
  }

  @Override
  public long deleteChecksums(
      String accountId, String orgId, String projectId, String entityId, EntityType entityType, String versionLabel) {
    Criteria criteria = Criteria.where(ConversionChecksumKeys.accountId).is(accountId);
    if (orgId != null) {
      criteria.and(ConversionChecksumKeys.orgId).is(orgId);
    }
    if (projectId != null) {
      criteria.and(ConversionChecksumKeys.projectId).is(projectId);
    }
    if (entityId != null) {
      criteria.and(ConversionChecksumKeys.entityId).is(entityId);
      criteria.and(ConversionChecksumKeys.entityType).is(entityType);
    }
    if (versionLabel != null) {
      criteria.and(ConversionChecksumKeys.versionLabel).is(versionLabel);
    }
    long deleted = conversionChecksumRepository.deleteByCriteria(criteria);
    log.info("[CONVERSION]: Deleted {} checksum records for account={}, org={}, project={}, entityId={}, entityType={}",
        deleted, accountId, orgId, projectId, entityId, entityType);
    return deleted;
  }
}
