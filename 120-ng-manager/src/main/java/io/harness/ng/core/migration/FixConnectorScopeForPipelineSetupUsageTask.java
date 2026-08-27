/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.encryption.Scope;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage.EntitySetupUsageKeys;
import io.harness.ng.core.migration.PipelineConnectorScopeMigrationStatus.PipelineConnectorScopeMigrationStatusKeys;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

/**
 * Background migration task to fix connector scope fields in entitySetupUsage collection
 * for remote Pipelines.
 *
 * Problem: Remote Pipelines were publishing Git connector references with the Pipeline's scope
 * instead of the connector's actual scope. This resulted in incorrect scope fields for
 * cross-scope connector references.
 *
 * Solution: This task fetches pipeline's connectorRef, parses the actual connector scope,
 * and updates the entitySetupUsage record with correct scope fields.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class FixConnectorScopeForPipelineSetupUsageTask implements Runnable {
  private static final String MIGRATION_LOG_PREFIX = "[FixConnectorScopeForPipelineSetupUsage]:";
  private static final String LOCK_NAME = "FixConnectorScopeForPipelineSetupUsageLock";
  private static final String MIGRATION_STATUS_ID = "PipelineConnectorScopeFix";

  private static final int BATCH_SIZE = 250;
  private static final long SLEEP_BETWEEN_BATCHES_MS = 2000L;
  private static final long MAX_RUNTIME_MS = 300000L; // 5 minutes
  private static final int LOCK_REFRESH_INTERVAL_SECONDS = 5;

  private static final int SCOPE_CACHE_MAX_SIZE = 5000;
  private static final int SCOPE_CACHE_EXPIRY_MINUTES = 30;

  private final MongoTemplate mongoTemplate;
  private final ScopeInfoClient scopeInfoClient;
  private final PersistentLocker persistentLocker;
  private final PipelineServiceClient pipelineServiceClient;

  private final Cache<String, ScopeInfo> scopeInfoCache =
      CacheBuilder.newBuilder()
          .maximumSize(SCOPE_CACHE_MAX_SIZE)
          .expireAfterWrite(SCOPE_CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
          .build();

  @Inject
  public FixConnectorScopeForPipelineSetupUsageTask(MongoTemplate mongoTemplate, ScopeInfoClient scopeInfoClient,
      PersistentLocker persistentLocker, PipelineServiceClient pipelineServiceClient) {
    this.mongoTemplate = mongoTemplate;
    this.scopeInfoClient = scopeInfoClient;
    this.persistentLocker = persistentLocker;
    this.pipelineServiceClient = pipelineServiceClient;
  }

  @Override
  public void run() {
    log.info("{} Starting migration task...", MIGRATION_LOG_PREFIX);

    if (getMaintenanceFlag()) {
      log.warn("{} Service is in maintenance mode. Skipping migration.", MIGRATION_LOG_PREFIX);
      return;
    }

    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(
             LOCK_NAME, Duration.ofSeconds(LOCK_REFRESH_INTERVAL_SECONDS))) {
      if (lock == null) {
        log.info("{} Failed to acquire lock. Another instance may be running. Skipping.", MIGRATION_LOG_PREFIX);
        return;
      }

      PipelineConnectorScopeMigrationStatus migrationStatus = getMigrationStatus();
      if (TRUE.equals(migrationStatus.getMigrationCompleted())) {
        log.info("{} Migration already completed. Skipping.", MIGRATION_LOG_PREFIX);
        return;
      }

      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      try {
        runMigration(migrationStatus);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
      }
    } catch (Exception e) {
      log.error("{} Exception during migration", MIGRATION_LOG_PREFIX, e);
    }
  }

  private void runMigration(PipelineConnectorScopeMigrationStatus migrationStatus) {
    log.info("{} Starting connector scope fix (max runtime: {} ms)", MIGRATION_LOG_PREFIX, MAX_RUNTIME_MS);

    long startTime = System.currentTimeMillis();
    long totalProcessed = 0;
    long totalUpdated = 0;
    long totalFailed = 0;

    try {
      while (true) {
        if (Thread.currentThread().isInterrupted()) {
          log.info("{} Thread interrupted. Saving progress and exiting.", MIGRATION_LOG_PREFIX);
          mongoTemplate.save(migrationStatus);
          return;
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        if (elapsedMs >= MAX_RUNTIME_MS) {
          log.info("{} Time budget exhausted after {} ms. Will resume in next run.", MIGRATION_LOG_PREFIX, elapsedMs);
          break;
        }

        List<EntitySetupUsage> batch = fetchBatch(migrationStatus.getLastProcessedTimestamp());

        if (batch.isEmpty()) {
          log.info("{} No more records to process. Migration complete.", MIGRATION_LOG_PREFIX);
          migrationStatus.setMigrationCompleted(TRUE);
          migrationStatus.setLastProcessedTimestamp(null);
          mongoTemplate.save(migrationStatus);
          break;
        }

        long batchUpdated = processBatch(batch);

        totalProcessed += batch.size();
        totalUpdated += batchUpdated;
        totalFailed += (batch.size() - batchUpdated);

        EntitySetupUsage lastEntity = batch.get(batch.size() - 1);
        migrationStatus.setLastProcessedTimestamp(lastEntity.getCreatedAt());
        mongoTemplate.save(migrationStatus);

        log.info("{} Processed batch of {} records: {} updated, {} failed", MIGRATION_LOG_PREFIX, batch.size(),
            batchUpdated, batch.size() - batchUpdated);

        sleepBetweenBatches();
      }
    } catch (Exception e) {
      log.error("{} Migration error after {} records", MIGRATION_LOG_PREFIX, totalProcessed, e);
      mongoTemplate.save(migrationStatus);
    }

    long duration = System.currentTimeMillis() - startTime;
    log.info("{} Migration completed: total_processed={}, total_updated={}, total_failed={}, duration_ms={}",
        MIGRATION_LOG_PREFIX, totalProcessed, totalUpdated, totalFailed, duration);
  }

  private List<EntitySetupUsage> fetchBatch(Long lastProcessedTimestamp) {
    Criteria criteria = Criteria.where(EntitySetupUsageKeys.nestedReferredByEntityType)
                            .is(EntityType.PIPELINES.name())
                            .and(EntitySetupUsageKeys.nestedReferredEntityType)
                            .is(EntityType.CONNECTORS.name());

    if (lastProcessedTimestamp != null) {
      criteria = criteria.and(EntitySetupUsageKeys.createdAt).gt(lastProcessedTimestamp);
    }

    Query query =
        new Query(criteria).with(Sort.by(Sort.Direction.ASC, EntitySetupUsageKeys.createdAt)).limit(BATCH_SIZE);

    return mongoTemplate.find(query, EntitySetupUsage.class);
  }

  private long processBatch(List<EntitySetupUsage> batch) {
    List<Pair<Query, Update>> bulkUpdates = new ArrayList<>();

    for (EntitySetupUsage entitySetupUsage : batch) {
      try {
        Pair<Query, Update> updatePair = processRecord(entitySetupUsage);
        if (updatePair != null) {
          bulkUpdates.add(updatePair);
        }
      } catch (Exception e) {
        log.warn("{} Error processing record {}: {}", MIGRATION_LOG_PREFIX, entitySetupUsage.getId(), e.getMessage());
      }
    }

    if (!bulkUpdates.isEmpty()) {
      try {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EntitySetupUsage.class);
        for (Pair<Query, Update> pair : bulkUpdates) {
          bulkOps.updateOne(pair.getFirst(), pair.getSecond());
        }
        bulkOps.execute();
      } catch (Exception e) {
        log.error("{} Bulk update failed for {} records", MIGRATION_LOG_PREFIX, bulkUpdates.size(), e);
      }
    }

    return bulkUpdates.size();
  }

  private Pair<Query, Update> processRecord(EntitySetupUsage entitySetupUsage) {
    EntityDetail referredEntity = entitySetupUsage.getReferredEntity();
    EntityDetail referredByEntity = entitySetupUsage.getReferredByEntity();

    if (referredEntity == null || referredEntity.getEntityRef() == null) {
      return null;
    }
    if (referredByEntity == null || referredByEntity.getEntityRef() == null) {
      return null;
    }

    IdentifierRef connectorRef = (IdentifierRef) referredEntity.getEntityRef();
    IdentifierRef pipelineRef = (IdentifierRef) referredByEntity.getEntityRef();

    String accountId = connectorRef.getAccountIdentifier();
    String connectorId = connectorRef.getIdentifier();
    String existingScope = connectorRef.getScope() != null ? connectorRef.getScope().name() : null;
    String existingOrgId = connectorRef.getOrgIdentifier();
    String existingProjectId = connectorRef.getProjectIdentifier();

    if (isEmpty(accountId) || isEmpty(connectorId)) {
      return null;
    }

    ScopeInfo existingScopeInfo = getConnectorScopeInfo(accountId, existingOrgId, existingProjectId);
    String existingParentUniqueId = existingScopeInfo != null ? existingScopeInfo.getUniqueId() : null;

    String pipelineAccountId = pipelineRef.getAccountIdentifier();
    String pipelineOrgId = pipelineRef.getOrgIdentifier();
    String pipelineProjectId = pipelineRef.getProjectIdentifier();
    String pipelineId = pipelineRef.getIdentifier();

    if (isEmpty(pipelineAccountId) || isEmpty(pipelineOrgId) || isEmpty(pipelineProjectId) || isEmpty(pipelineId)) {
      return null;
    }

    String pipelineConnectorRef =
        getPipelineConnectorRef(pipelineAccountId, pipelineOrgId, pipelineProjectId, pipelineId);
    if (isEmpty(pipelineConnectorRef)) {
      return null;
    }

    Scope correctScope = parseConnectorScope(pipelineConnectorRef);
    String correctOrgId = null;
    String correctProjectId = null;

    if (correctScope == Scope.ORG) {
      correctOrgId = pipelineOrgId;
    } else if (correctScope == Scope.PROJECT) {
      correctOrgId = pipelineOrgId;
      correctProjectId = pipelineProjectId;
    }

    ScopeInfo computedScopeInfo = getConnectorScopeInfo(accountId, correctOrgId, correctProjectId);
    if (computedScopeInfo == null) {
      log.warn("{} Could not resolve ScopeInfo for record {} with scope {}", MIGRATION_LOG_PREFIX,
          entitySetupUsage.getId(), correctScope);
      return null;
    }

    String correctParentUniqueId = computedScopeInfo.getUniqueId();

    if (Objects.equals(correctParentUniqueId, existingParentUniqueId)) {
      return null;
    }

    String correctFQN = computeFQN(accountId, correctOrgId, correctProjectId, connectorId);

    Query query = new Query(Criteria.where(EntitySetupUsageKeys.id).is(entitySetupUsage.getId()));
    Update update = new Update()
                        .set(EntitySetupUsageKeys.referredEntityScope, correctScope.name())
                        .set(EntitySetupUsageKeys.referredEntityParentUniqueId, correctParentUniqueId)
                        .set(EntitySetupUsageKeys.referredEntityFQN, correctFQN);

    if (correctOrgId != null) {
      update.set(EntitySetupUsageKeys.referredEntityOrgIdentifier, correctOrgId);
    } else {
      update.unset(EntitySetupUsageKeys.referredEntityOrgIdentifier);
    }

    if (correctProjectId != null) {
      update.set(EntitySetupUsageKeys.referredEntityProjectIdentifier, correctProjectId);
    } else {
      update.unset(EntitySetupUsageKeys.referredEntityProjectIdentifier);
    }

    log.debug("{} Updating {}: scope {} -> {}, org {} -> {}, project {} -> {}, parentUniqueId {} -> {}",
        MIGRATION_LOG_PREFIX, entitySetupUsage.getId(), existingScope, correctScope, existingOrgId, correctOrgId,
        existingProjectId, correctProjectId, existingParentUniqueId, correctParentUniqueId);

    return Pair.of(query, update);
  }

  private String getPipelineConnectorRef(String accountId, String orgId, String projectId, String pipelineId) {
    try {
      PMSPipelineResponseDTO response = NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(
          pipelineId, accountId, orgId, projectId, null, null, false, "true"));
      return response != null ? response.getConnectorRef() : null;
    } catch (Exception e) {
      log.warn(
          "{} Error fetching pipeline {}/{}/{}/{}", MIGRATION_LOG_PREFIX, accountId, orgId, projectId, pipelineId, e);
      return null;
    }
  }

  private Scope parseConnectorScope(String connectorRef) {
    if (isEmpty(connectorRef)) {
      return Scope.PROJECT;
    }
    if (connectorRef.startsWith("account.")) {
      return Scope.ACCOUNT;
    } else if (connectorRef.startsWith("org.")) {
      return Scope.ORG;
    }
    return Scope.PROJECT;
  }

  private ScopeInfo getConnectorScopeInfo(String accountId, String orgId, String projectId) {
    String cacheKey = format("%s/%s/%s", accountId, orgId != null ? orgId : "", projectId != null ? projectId : "");

    ScopeInfo cached = scopeInfoCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }

    try {
      ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountId, orgId, projectId));
      if (scopeInfo != null) {
        scopeInfoCache.put(cacheKey, scopeInfo);
      }
      return scopeInfo;
    } catch (Exception e) {
      log.warn("{} Error fetching ScopeInfo for {}/{}/{}", MIGRATION_LOG_PREFIX, accountId, orgId, projectId);
      return null;
    }
  }

  private String computeFQN(String accountId, String orgId, String projectId, String identifier) {
    StringBuilder fqn = new StringBuilder(accountId);
    if (isNotEmpty(orgId)) {
      fqn.append('/').append(orgId);
    }
    if (isNotEmpty(projectId)) {
      fqn.append('/').append(projectId);
    }
    return fqn.append('/').append(identifier).toString();
  }

  private PipelineConnectorScopeMigrationStatus getMigrationStatus() {
    PipelineConnectorScopeMigrationStatus status = mongoTemplate.findOne(
        new Query(Criteria.where(PipelineConnectorScopeMigrationStatusKeys.id).is(MIGRATION_STATUS_ID)),
        PipelineConnectorScopeMigrationStatus.class);

    if (status == null) {
      status =
          PipelineConnectorScopeMigrationStatus.builder().id(MIGRATION_STATUS_ID).migrationCompleted(false).build();
    }
    return status;
  }

  private void sleepBetweenBatches() {
    try {
      Thread.sleep(SLEEP_BETWEEN_BATCHES_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("{} Sleep interrupted", MIGRATION_LOG_PREFIX);
    }
  }
}
