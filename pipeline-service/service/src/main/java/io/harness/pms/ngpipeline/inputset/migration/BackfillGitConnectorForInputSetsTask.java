/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.migration;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import static java.lang.Boolean.TRUE;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.ff.FeatureFlagService;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.migration.InputSetConnectorBackfillMigrationStatus.InputSetConnectorBackfillMigrationStatusKeys;
import io.harness.pms.ngpipeline.inputset.setupusage.InputSetSetupUsageHelper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Background migration task to backfill Git connector references in entitySetupUsage for remote InputSets.
 *
 * Problem: Remote input sets created before the forward fix never published their Git connector references.
 * The "Referenced By" tab on connectors doesn't show these input sets.
 *
 * Solution: For each remote input set, fetch from Git (default branch) and publish connector reference
 * with entityGitMetadata. Input sets not on default branch are skipped (file not found).
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BackfillGitConnectorForInputSetsTask implements Runnable {
  private static final String MIGRATION_LOG_PREFIX = "[BackfillGitConnectorForInputSets]:";
  private static final String LOCK_NAME = "BackfillGitConnectorForInputSetsLock";
  private static final String MIGRATION_STATUS_ID = "InputSetConnectorBackfill";

  private static final int BATCH_SIZE = 100;
  private static final long SLEEP_BETWEEN_BATCHES_MS = 10000L;
  private static final long MAX_RUNTIME_MS = 300000L;
  private static final int LOCK_REFRESH_INTERVAL_SECONDS = 5;

  private static final int SCOPE_CACHE_MAX_SIZE = 5000;
  private static final int SCOPE_CACHE_EXPIRY_MINUTES = 30;

  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final InputSetSetupUsageHelper inputSetSetupUsageHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final FeatureFlagService featureFlagService;

  private final Cache<String, ScopeInfo> scopeInfoCache =
      CacheBuilder.newBuilder()
          .maximumSize(SCOPE_CACHE_MAX_SIZE)
          .expireAfterWrite(SCOPE_CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
          .build();

  @Inject
  public BackfillGitConnectorForInputSetsTask(MongoTemplate mongoTemplate, PersistentLocker persistentLocker,
      InputSetSetupUsageHelper inputSetSetupUsageHelper, ScopeResolutionHelper scopeResolutionHelper,
      PmsFeatureFlagHelper pmsFeatureFlagHelper, GitAwareEntityHelper gitAwareEntityHelper,
      FeatureFlagService featureFlagService) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.inputSetSetupUsageHelper = inputSetSetupUsageHelper;
    this.scopeResolutionHelper = scopeResolutionHelper;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.gitAwareEntityHelper = gitAwareEntityHelper;
    this.featureFlagService = featureFlagService;
  }

  @Override
  public void run() {
    log.info("{} Starting migration task...", MIGRATION_LOG_PREFIX);

    if (getMaintenanceFlag()) {
      log.warn("{} Service is in maintenance mode. Skipping migration.", MIGRATION_LOG_PREFIX);
      return;
    }

    if (featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION)) {
      log.info("{} Migration disabled by feature flag: {}", MIGRATION_LOG_PREFIX,
          FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION);
      return;
    }

    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(
             LOCK_NAME, Duration.ofSeconds(LOCK_REFRESH_INTERVAL_SECONDS))) {
      if (lock == null) {
        log.info("{} Failed to acquire lock. Another instance may be running. Skipping.", MIGRATION_LOG_PREFIX);
        return;
      }

      InputSetConnectorBackfillMigrationStatus migrationStatus = getMigrationStatus();
      if (TRUE.equals(migrationStatus.getMigrationCompleted())) {
        log.info("{} Migration already completed. Skipping.", MIGRATION_LOG_PREFIX);
        return;
      }

      setupServicePrincipal();
      try {
        runMigration(migrationStatus);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
      }
    } catch (Exception e) {
      log.error("{} Exception during migration", MIGRATION_LOG_PREFIX, e);
    }
  }

  private void setupServicePrincipal() {
    ServicePrincipal servicePrincipal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    SourcePrincipalContextBuilder.setSourcePrincipal(servicePrincipal);
    SecurityContextBuilder.setContext(servicePrincipal);
  }

  private void runMigration(InputSetConnectorBackfillMigrationStatus migrationStatus) {
    log.info("{} Starting Git connector backfill (max runtime: {} ms)", MIGRATION_LOG_PREFIX, MAX_RUNTIME_MS);

    long startTime = System.currentTimeMillis();
    long runProcessed = 0;
    long runGitCalls = 0;

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

        List<InputSetEntity> batch = fetchBatch(migrationStatus);

        if (batch.isEmpty()) {
          log.info("{} No more records to process. Migration complete.", MIGRATION_LOG_PREFIX);
          migrationStatus.setMigrationCompleted(TRUE);
          migrationStatus.setLastProcessedTimestamp(null);
          migrationStatus.setLastProcessedUuid(null);
          mongoTemplate.save(migrationStatus);
          break;
        }

        long batchGitCalls = processBatch(batch);
        runProcessed += batch.size();
        runGitCalls += batchGitCalls;

        InputSetEntity lastEntity = batch.get(batch.size() - 1);
        migrationStatus.setLastProcessedTimestamp(lastEntity.getCreatedAt());
        migrationStatus.setLastProcessedUuid(lastEntity.getUuid());
        mongoTemplate.save(migrationStatus);

        log.info("{} Processed batch of {} records, gitCalls={}", MIGRATION_LOG_PREFIX, batch.size(), batchGitCalls);

        sleepBetweenBatches();
      }
    } catch (Exception e) {
      log.error("{} Migration error after {} records", MIGRATION_LOG_PREFIX, runProcessed, e);
    }

    migrationStatus.setTotalProcessed(safeAdd(migrationStatus.getTotalProcessed(), runProcessed));
    migrationStatus.setTotalGitCalls(safeAdd(migrationStatus.getTotalGitCalls(), runGitCalls));
    mongoTemplate.save(migrationStatus);

    long duration = System.currentTimeMillis() - startTime;
    log.info(
        "{} Run completed: processed={}, gitCalls={}, cumulative_processed={}, cumulative_gitCalls={}, duration={}ms",
        MIGRATION_LOG_PREFIX, runProcessed, runGitCalls, migrationStatus.getTotalProcessed(),
        migrationStatus.getTotalGitCalls(), duration);
  }

  private long safeAdd(Long existing, long toAdd) {
    return (existing == null ? 0L : existing) + toAdd;
  }

  private List<InputSetEntity> fetchBatch(InputSetConnectorBackfillMigrationStatus migrationStatus) {
    Long lastProcessedTimestamp = migrationStatus.getLastProcessedTimestamp();
    String lastProcessedUuid = migrationStatus.getLastProcessedUuid();

    Criteria criteria = Criteria.where(InputSetEntityKeys.storeType)
                            .is(StoreType.REMOTE)
                            .and(InputSetEntityKeys.connectorRef)
                            .nin(null, "")
                            .and(InputSetEntityKeys.deleted)
                            .ne(true);

    if (lastProcessedTimestamp != null) {
      String cursorUuid = isNotEmpty(lastProcessedUuid) ? lastProcessedUuid : "";
      criteria = criteria.andOperator(
          new Criteria().orOperator(Criteria.where(InputSetEntityKeys.createdAt).gt(lastProcessedTimestamp),
              new Criteria().andOperator(Criteria.where(InputSetEntityKeys.createdAt).is(lastProcessedTimestamp),
                  Criteria.where(InputSetEntityKeys.uuid).gt(cursorUuid))));
    }

    Query query =
        new Query(criteria)
            .with(Sort.by(Sort.Order.asc(InputSetEntityKeys.createdAt), Sort.Order.asc(InputSetEntityKeys.uuid)))
            .limit(BATCH_SIZE);

    return mongoTemplate.find(query, InputSetEntity.class);
  }

  private long processBatch(List<InputSetEntity> batch) {
    long gitCalls = 0;
    for (InputSetEntity inputSet : batch) {
      try {
        if (isEmpty(inputSet.getConnectorRef())) {
          continue;
        }
        gitCalls += publishConnectorReference(inputSet);
      } catch (Exception e) {
        log.warn(
            "{} Error processing input set {}: {}", MIGRATION_LOG_PREFIX, inputSet.getIdentifier(), e.getMessage());
      }
    }
    return gitCalls;
  }

  private int publishConnectorReference(InputSetEntity inputSet) {
    if (GitAwareContextHelper.isNullOrDefault(inputSet.getConnectorRef())) {
      return 0;
    }

    ScmGitMetaData gitMetaData = fetchInputSetFromGit(inputSet);
    if (gitMetaData == null) {
      return 1; // Failed fetch attempt counts as git call (cache miss + git error)
    }

    int gitCallCount = isGitApiCall(gitMetaData) ? 1 : 0;

    String branch = gitMetaData.getBranchName();
    String repo = gitMetaData.getRepoName();

    ScopeInfo scopeInfo = null;
    if (inputSet.getParentUniqueId() != null) {
      scopeInfo = getCachedScopeInfo(inputSet.getAccountId(), inputSet.getParentUniqueId());
    }

    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSet, scopeInfo, true, branch, repo);

    log.debug("{} Published connector reference for input set {}/{}/{}/{} with branch={}, repo={}",
        MIGRATION_LOG_PREFIX, inputSet.getAccountId(), inputSet.getOrgIdentifier(), inputSet.getProjectIdentifier(),
        inputSet.getIdentifier(), branch, repo);
    return gitCallCount;
  }

  private boolean isGitApiCall(ScmGitMetaData gitMetaData) {
    CacheResponse cacheResponse = gitMetaData.getCacheResponse();
    return cacheResponse == null || !CacheState.VALID_CACHE.equals(cacheResponse.getCacheState());
  }

  private ScmGitMetaData fetchInputSetFromGit(InputSetEntity inputSet) {
    try {
      Scope scope = Scope.of(inputSet.getAccountId(), inputSet.getOrgIdentifier(), inputSet.getProjectIdentifier());
      GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                            .branchName("")
                                                            .connectorRef(inputSet.getConnectorRef())
                                                            .filePath(inputSet.getFilePath())
                                                            .repoName(inputSet.getRepo())
                                                            .entityType(EntityType.INPUT_SETS)
                                                            .loadFromCache(true)
                                                            .build();

      gitAwareEntityHelper.fetchEntityFromRemote(inputSet, scope, gitContextRequestParams, Collections.emptyMap());

      return GitAwareContextHelper.getScmGitMetaData();
    } catch (Exception e) {
      log.info("{} Skipping input set {}/{}/{}/{}: {}", MIGRATION_LOG_PREFIX, inputSet.getAccountId(),
          inputSet.getOrgIdentifier(), inputSet.getProjectIdentifier(), inputSet.getIdentifier(),
          e.getClass().getSimpleName());
      log.debug("{} Git fetch error for input set {}: ", MIGRATION_LOG_PREFIX, inputSet.getIdentifier(), e);
      return null;
    }
  }

  private ScopeInfo getCachedScopeInfo(String accountId, String parentUniqueId) {
    String cacheKey = accountId + "/" + parentUniqueId;
    ScopeInfo cached = scopeInfoCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, parentUniqueId);
    if (scopeInfo != null) {
      scopeInfoCache.put(cacheKey, scopeInfo);
    }
    return scopeInfo;
  }

  private InputSetConnectorBackfillMigrationStatus getMigrationStatus() {
    InputSetConnectorBackfillMigrationStatus status = mongoTemplate.findOne(
        new Query(Criteria.where(InputSetConnectorBackfillMigrationStatusKeys.id).is(MIGRATION_STATUS_ID)),
        InputSetConnectorBackfillMigrationStatus.class);

    if (status == null) {
      status =
          InputSetConnectorBackfillMigrationStatus.builder().id(MIGRATION_STATUS_ID).migrationCompleted(false).build();
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
