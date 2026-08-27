/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ng.core.migration;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entities.migration.RecomputeParentUniqueIdMigrationStatus;
import io.harness.ng.core.entities.migration.RecomputeParentUniqueIdMigrationStatus.RecomputeParentUniqueIdMigrationStatusKeys;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage.EntitySetupUsageKeys;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class RecomputeParentUniqueIdForEntitySetupUsageTask implements Runnable {
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private static final String LOCK_NAME_PREFIX = "RecomputeParentUniqueIdForEntitySetupUsageLock";
  private static final String NG_MANAGER_ENTITIES_MIGRATION_LOG = "[RecomputeParentUniqueIdForEntitySetupUsageTask]:";
  private static final String MIGRATION_STATUS_ID = "EntitySetupUsage_referredByEntity_parentUniqueId";
  private static final long SLEEP_DELAY_MS = Long.parseLong(
      System.getenv().getOrDefault("RERUN_ENTITY_SETUP_USAGE_MIGRATION_FOR_PIPELINES_SLEEP_DELAY_MS", "2000"));
  private static final int BATCH_SIZE = Integer.parseInt(
      System.getenv().getOrDefault("RERUN_ENTITY_SETUP_USAGE_MIGRATION_FOR_PIPELINES_BATCH_SIZE", "500"));

  @Inject
  public RecomputeParentUniqueIdForEntitySetupUsageTask(
      MongoTemplate mongoTemplate, PersistentLocker persistentLocker, ScopeResolutionHelper scopeResolutionHelper) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public void run() {
    long startTime = System.currentTimeMillis();
    int totalProcessed = 0;
    int totalUpdated = 0;
    int totalFailed = 0;

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock during parentId migration task", NG_MANAGER_ENTITIES_MIGRATION_LOG));
        return;
      }
      try {
        log.info(format("%s Migration started", NG_MANAGER_ENTITIES_MIGRATION_LOG));

        RecomputeParentUniqueIdMigrationStatus migrationStatus = mongoTemplate.findOne(
            new Query(Criteria.where(RecomputeParentUniqueIdMigrationStatusKeys.id).is(MIGRATION_STATUS_ID)),
            RecomputeParentUniqueIdMigrationStatus.class);

        Long lastProcessedTimestamp = migrationStatus != null ? migrationStatus.getLastProcessedTimestamp() : null;
        List<EntitySetupUsage> entitySetupUsages;

        while (true) {
          Criteria criteria =
              Criteria.where(EntitySetupUsageKeys.nestedReferredByEntityType).is(EntityType.PIPELINES.name());
          if (lastProcessedTimestamp != null) {
            criteria = criteria.and(EntitySetupUsageKeys.createdAt).gt(lastProcessedTimestamp);
          }
          Query query =
              new Query(criteria).with(Sort.by(Sort.Direction.ASC, EntitySetupUsageKeys.createdAt)).limit(BATCH_SIZE);

          entitySetupUsages = mongoTemplate.find(query, EntitySetupUsage.class);
          if (entitySetupUsages.isEmpty()) {
            break;
          }
          log.info(format("%s Processing batch of %d EntitySetupUsage records with referredByEntityType as PIPELINES",
              NG_MANAGER_ENTITIES_MIGRATION_LOG, entitySetupUsages.size()));

          List<Pair<Query, Update>> bulkUpdates = new ArrayList<>();
          for (EntitySetupUsage entitySetupUsage : entitySetupUsages) {
            EntityDetail referredByEntity = entitySetupUsage.getReferredByEntity();
            if (referredByEntity != null && referredByEntity.getEntityRef() != null) {
              IdentifierRef entityRef = (IdentifierRef) referredByEntity.getEntityRef();
              String accountIdentifier = entityRef.getAccountIdentifier();
              String orgIdentifier = entityRef.getOrgIdentifier();
              String projectIdentifier = entityRef.getProjectIdentifier();
              String existingParentUniqueId = entityRef.getParentUniqueId();

              try {
                ScopeInfo scopeInfo = null;

                if (existingParentUniqueId != null) {
                  try {
                    scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, existingParentUniqueId);
                    log.info(format("%s Resolved scope for entity %s using parentUniqueId: parentUniqueId=%s",
                        NG_MANAGER_ENTITIES_MIGRATION_LOG, entitySetupUsage.getId(), scopeInfo.getUniqueId()));
                  } catch (Exception e) {
                    log.info(format("%s Failed to resolve scope using parentUniqueId for entity %s, falling back to "
                            + "org/project: %s",
                        NG_MANAGER_ENTITIES_MIGRATION_LOG, entitySetupUsage.getId(), e.getMessage()));
                  }
                }

                if (scopeInfo == null) {
                  scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
                  log.info(format("%s Resolved scope for entity %s using org/project: parentUniqueId=%s (account=%s, "
                          + "org=%s, project=%s)",
                      NG_MANAGER_ENTITIES_MIGRATION_LOG, entitySetupUsage.getId(), scopeInfo.getUniqueId(),
                      accountIdentifier, orgIdentifier, projectIdentifier));
                }

                Query updateQuery = new Query(Criteria.where(EntitySetupUsageKeys.id).is(entitySetupUsage.getId()));
                Update update =
                    new Update().set(EntitySetupUsageKeys.referredByEntityParentUniqueId, scopeInfo.getUniqueId());
                bulkUpdates.add(Pair.of(updateQuery, update));
              } catch (Exception e) {
                log.warn(format("%s Could not resolve scope for entity %s with account=%s, org=%s, project=%s: %s - %s",
                    NG_MANAGER_ENTITIES_MIGRATION_LOG, entitySetupUsage.getId(), accountIdentifier, orgIdentifier,
                    projectIdentifier, e.getClass().getSimpleName(), e.getMessage()));
              }
            }
          }

          // Update metrics
          totalProcessed += entitySetupUsages.size();
          totalUpdated += bulkUpdates.size();
          totalFailed += (entitySetupUsages.size() - bulkUpdates.size());

          if (!bulkUpdates.isEmpty()) {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EntitySetupUsage.class);
            for (Pair<Query, Update> pair : bulkUpdates) {
              bulkOps.updateOne(pair.getFirst(), pair.getSecond());
            }
            bulkOps.execute();
          }

          log.info(format("%s Processed batch of %d records: %d updated, %d failed", NG_MANAGER_ENTITIES_MIGRATION_LOG,
              entitySetupUsages.size(), bulkUpdates.size(), entitySetupUsages.size() - bulkUpdates.size()));

          // Always update timestamp to prevent infinite loop on failed batches
          EntitySetupUsage lastEntity = entitySetupUsages.get(entitySetupUsages.size() - 1);
          lastProcessedTimestamp = lastEntity.getCreatedAt();
          mongoTemplate.save(RecomputeParentUniqueIdMigrationStatus.builder()
                                 .id(MIGRATION_STATUS_ID)
                                 .lastProcessedEntityId(lastEntity.getId())
                                 .lastProcessedTimestamp(lastProcessedTimestamp)
                                 .build());

          // Always sleep between batches to prevent DB hammering
          Thread.sleep(SLEEP_DELAY_MS);
        }

        // Log final metrics
        long duration = System.currentTimeMillis() - startTime;
        log.info(format("%s Migration completed: total_processed=%d, total_updated=%d, total_failed=%d, duration_ms=%d",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, totalProcessed, totalUpdated, totalFailed, duration));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        long duration = System.currentTimeMillis() - startTime;
        log.warn(
            format("%s Migration interrupted: total_processed=%d, total_updated=%d, total_failed=%d, duration_ms=%d",
                NG_MANAGER_ENTITIES_MIGRATION_LOG, totalProcessed, totalUpdated, totalFailed, duration));
      } catch (Exception e) {
        long duration = System.currentTimeMillis() - startTime;
        log.error(format("%s Error during migration: %s - %s (total_processed=%d, total_updated=%d, total_failed=%d, "
                          + "duration_ms=%d)",
                      NG_MANAGER_ENTITIES_MIGRATION_LOG, e.getClass().getSimpleName(), e.getMessage(), totalProcessed,
                      totalUpdated, totalFailed, duration),
            e);
      }
    }
  }
}
