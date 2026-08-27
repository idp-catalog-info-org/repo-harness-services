/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.timescaledb.tables.Environments.ENVIRONMENTS;
import static io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES;
import static io.harness.timescaledb.tables.Services.SERVICES;
import static io.harness.timescaledb.tables.StageExecution.STAGE_EXECUTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.config.ServiceUniqueIdBackfillConfig;
import io.harness.timescaledb.tables.StageExecution;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ServiceUniqueIdBackfillTask implements Runnable {
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final DSLContext dslContext;

  @Inject @Named("serviceUniqueIdBackfillJobConfig") private ServiceUniqueIdBackfillConfig config;

  private static final String DEBUG_MESSAGE = "ServiceUniqueIdBackfillTask: ";
  private static final String LOCK_NAME = "ServiceUniqueIdBackfillTaskLock";
  private static final int LOCK_REFRESH_INTERVAL_SECONDS = 5;
  private static final int CACHE_MAX_SIZE = 10000;
  private static final int CACHE_EXPIRY_MINUTES = 30;

  // Dummy value written to unique_id columns when the corresponding entity cannot be resolved.
  // This prevents unresolvable records from being re-fetched on every migration run (they no longer match IS NULL).
  static final String UNKNOWN_UNIQUE_ID = "UNKNOWN";

  private static final Field<String> STAGE_EXEC_ID = StageExecution.STAGE_EXECUTION.ID;

  private static final Table<?> CD_STAGE_EXECUTION = DSL.table("cd_stage_execution");
  private static final Field<String> CD_STAGE_EXECUTION_ID = DSL.field("id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_SERVICE_ID = DSL.field("service_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_SERVICE_UNIQUE_ID =
      DSL.field("service_unique_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_SERVICE_PARENT_UNIQUE_ID =
      DSL.field("service_parent_unique_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_ENV_ID = DSL.field("env_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_ENV_UNIQUE_ID = DSL.field("env_unique_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_ENV_PARENT_UNIQUE_ID =
      DSL.field("env_parent_unique_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_INFRA_ID = DSL.field("infra_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_INFRA_UNIQUE_ID = DSL.field("infra_unique_id", String.class);
  private static final Field<String> CD_STAGE_EXECUTION_INFRA_PARENT_UNIQUE_ID =
      DSL.field("infra_parent_unique_id", String.class);
  private static final int MONGO_RETRY_COUNT = 3;
  private static final long MONGO_RETRY_DELAY_MS = 1000;

  // Bounded cache for service lookups with eviction policy
  private final Cache<String, String[]> serviceUniqueIdCache =
      CacheBuilder.newBuilder()
          .maximumSize(CACHE_MAX_SIZE)
          .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
          .build();

  // Bounded cache for environment lookups with eviction policy
  private final Cache<String, String[]> envUniqueIdCache = CacheBuilder.newBuilder()
                                                               .maximumSize(CACHE_MAX_SIZE)
                                                               .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
                                                               .build();

  // Bounded cache for infrastructure lookups with eviction policy
  private final Cache<String, String[]> infraUniqueIdCache =
      CacheBuilder.newBuilder()
          .maximumSize(CACHE_MAX_SIZE)
          .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
          .build();

  @Inject
  public ServiceUniqueIdBackfillTask(
      MongoTemplate mongoTemplate, PersistentLocker persistentLocker, DSLContext dslContext) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.dslContext = dslContext;
  }

  @Override
  public void run() {
    log.info("{} starting...", DEBUG_MESSAGE);

    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(
             LOCK_NAME, Duration.ofSeconds(LOCK_REFRESH_INTERVAL_SECONDS))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "Failed to acquire lock. Skipping this run.");
        return;
      }

      runMigration();
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Exception during migration", e);
    }
  }

  private void runMigration() {
    log.info(DEBUG_MESSAGE + "Starting migration to backfill service_unique_id, env_unique_id and infra_unique_id");

    int totalRecordsUpdated = 0;
    int retry = 0;
    int batchNumber = 0;
    int batchSize = config.getBatchSize();
    int maxRetryCount = config.getMaxRetryCount();
    int sleepBetweenBatchesInMillis = config.getSleepBetweenBatchesInMillis();

    while (retry < maxRetryCount) {
      batchNumber++;

      List<CdStageExecutionRecord> batchRecords = fetchBatchRecords(batchSize);

      if (batchRecords.isEmpty()) {
        log.info(DEBUG_MESSAGE + "No more records to process. Migration complete.");
        break;
      }

      try {
        int updatedInBatch = processBatch(batchRecords);
        totalRecordsUpdated += updatedInBatch;

        log.info(DEBUG_MESSAGE + "Processed batch #{}. Updated: {}, Total updated so far: {}", batchNumber,
            updatedInBatch, totalRecordsUpdated);
        Thread.sleep(sleepBetweenBatchesInMillis);
      } catch (InterruptedException e) {
        log.warn(DEBUG_MESSAGE + "Task interrupted while processing batch", e);
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        log.error(DEBUG_MESSAGE + "Exception while processing batch #{}", batchNumber, e);
        retry++;
      }
    }

    log.info(DEBUG_MESSAGE + "Run complete. Total records updated: {}", totalRecordsUpdated);
  }

  private List<CdStageExecutionRecord> fetchBatchRecords(int batchSize) {
    List<CdStageExecutionRecord> records = new ArrayList<>();

    try {
      // Step 1: Fetch batch of records from cd_stage_execution that need backfill
      // Records where service_unique_id OR env_unique_id OR infra_unique_id is still null
      Result<? extends Record> cseRecords =
          dslContext
              .select(CD_STAGE_EXECUTION_ID, CD_STAGE_EXECUTION_SERVICE_ID, CD_STAGE_EXECUTION_ENV_ID,
                  CD_STAGE_EXECUTION_SERVICE_UNIQUE_ID, CD_STAGE_EXECUTION_ENV_UNIQUE_ID, CD_STAGE_EXECUTION_INFRA_ID,
                  CD_STAGE_EXECUTION_INFRA_UNIQUE_ID)
              .from(CD_STAGE_EXECUTION)
              .where(CD_STAGE_EXECUTION_SERVICE_UNIQUE_ID.isNull()
                         .and(CD_STAGE_EXECUTION_SERVICE_ID.isNotNull())
                         .or(CD_STAGE_EXECUTION_ENV_UNIQUE_ID.isNull().and(CD_STAGE_EXECUTION_ENV_ID.isNotNull()))
                         .or(CD_STAGE_EXECUTION_INFRA_UNIQUE_ID.isNull().and(CD_STAGE_EXECUTION_INFRA_ID.isNotNull())))
              .limit(batchSize)
              .fetch();

      if (cseRecords.isEmpty()) {
        return records;
      }

      // Extract IDs for lookup
      List<String> stageExecutionIds =
          cseRecords.stream().map(r -> r.get(CD_STAGE_EXECUTION_ID)).collect(Collectors.toList());

      // Step 2: Fetch scope info from stage_execution only for the batch IDs
      Map<String, ScopeInfo> scopeInfoMap = new HashMap<>();
      Result<Record4<String, String, String, String>> scopeRecords =
          dslContext
              .select(STAGE_EXEC_ID, STAGE_EXECUTION.ACCOUNT_IDENTIFIER, STAGE_EXECUTION.ORG_IDENTIFIER,
                  STAGE_EXECUTION.PROJECT_IDENTIFIER)
              .from(STAGE_EXECUTION)
              .where(STAGE_EXEC_ID.in(stageExecutionIds))
              .fetch();

      // Build lookup map
      for (Record4<String, String, String, String> scopeRecord : scopeRecords) {
        scopeInfoMap.put(scopeRecord.get(STAGE_EXEC_ID),
            new ScopeInfo(scopeRecord.get(STAGE_EXECUTION.ACCOUNT_IDENTIFIER),
                scopeRecord.get(STAGE_EXECUTION.ORG_IDENTIFIER), scopeRecord.get(STAGE_EXECUTION.PROJECT_IDENTIFIER)));
      }

      // Combine results
      for (Record cseRecord : cseRecords) {
        String id = cseRecord.get(CD_STAGE_EXECUTION_ID);
        String serviceId = cseRecord.get(CD_STAGE_EXECUTION_SERVICE_ID);
        String envId = cseRecord.get(CD_STAGE_EXECUTION_ENV_ID);
        String infraId = cseRecord.get(CD_STAGE_EXECUTION_INFRA_ID);
        String existingServiceUniqueId = cseRecord.get(CD_STAGE_EXECUTION_SERVICE_UNIQUE_ID);
        String existingEnvUniqueId = cseRecord.get(CD_STAGE_EXECUTION_ENV_UNIQUE_ID);
        String existingInfraUniqueId = cseRecord.get(CD_STAGE_EXECUTION_INFRA_UNIQUE_ID);
        ScopeInfo scope = scopeInfoMap.get(id);

        // Only include fields that still need backfill
        boolean needsServiceBackfill = existingServiceUniqueId == null && serviceId != null;
        boolean needsEnvBackfill = existingEnvUniqueId == null && envId != null;
        boolean needsInfraBackfill = existingInfraUniqueId == null && infraId != null;

        if (scope != null) {
          records.add(new CdStageExecutionRecord(id, needsServiceBackfill ? serviceId : null,
              needsEnvBackfill ? envId : null, needsInfraBackfill ? infraId : null, envId, scope.accountIdentifier,
              scope.orgIdentifier, scope.projectIdentifier));
        } else {
          // No scope info found - include as orphaned record (null accountIdentifier)
          // so processBatch can stamp sentinel values and the migration doesn't get stuck
          log.warn(DEBUG_MESSAGE + "No scope info found for stage_execution_id: {}. Will mark with sentinel.", id);
          records.add(new CdStageExecutionRecord(id, needsServiceBackfill ? serviceId : null,
              needsEnvBackfill ? envId : null, needsInfraBackfill ? infraId : null, envId, null, null, null));
        }
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error fetching batch records", e);
    }
    return records;
  }

  private int processBatch(List<CdStageExecutionRecord> batchRecords) {
    int updatedCount = 0;
    List<MongoUpdateInfo> mongoUpdates = new ArrayList<>();
    List<TimescaleUpdateInfo> tsUpdates = new ArrayList<>();

    for (CdStageExecutionRecord cseRecord : batchRecords) {
      String serviceUniqueId = null;
      String serviceParentUniqueId = null;
      String envUniqueId = null;
      String envParentUniqueId = null;
      String infraUniqueId = null;
      String infraParentUniqueId = null;

      boolean isOrphaned = cseRecord.accountIdentifier == null;

      // Resolve service unique IDs if service_id needs backfill
      if (cseRecord.serviceId != null) {
        if (isOrphaned) {
          // No scope info available - use sentinel to avoid re-processing
          serviceUniqueId = UNKNOWN_UNIQUE_ID;
          serviceParentUniqueId = UNKNOWN_UNIQUE_ID;
        } else {
          ScopedIdInfo serviceIdInfo =
              parseScopedId(cseRecord.serviceId, cseRecord.orgIdentifier, cseRecord.projectIdentifier);
          if (serviceIdInfo != null) {
            String[] uniqueIds = lookupServiceUniqueId(cseRecord.accountIdentifier, serviceIdInfo.orgIdentifier,
                serviceIdInfo.projectIdentifier, serviceIdInfo.identifier);
            if (uniqueIds != null) {
              serviceUniqueId = uniqueIds[0];
              serviceParentUniqueId = uniqueIds[1];
            } else {
              // Service entity not found in DB - use sentinel to avoid re-processing
              log.info(DEBUG_MESSAGE + "Service not found for record {}, service_id: {}. Marking as UNKNOWN.",
                  cseRecord.id, cseRecord.serviceId);
              serviceUniqueId = UNKNOWN_UNIQUE_ID;
              serviceParentUniqueId = UNKNOWN_UNIQUE_ID;
            }
          } else {
            // Unable to parse service_id - use sentinel
            log.info(DEBUG_MESSAGE + "Unable to parse service_id for record {}: {}. Marking as UNKNOWN.", cseRecord.id,
                cseRecord.serviceId);
            serviceUniqueId = UNKNOWN_UNIQUE_ID;
            serviceParentUniqueId = UNKNOWN_UNIQUE_ID;
          }
        }
      }

      // Resolve env unique IDs if env_id needs backfill
      if (cseRecord.envId != null) {
        if (isOrphaned) {
          // No scope info available - use sentinel to avoid re-processing
          envUniqueId = UNKNOWN_UNIQUE_ID;
          envParentUniqueId = UNKNOWN_UNIQUE_ID;
        } else {
          ScopedIdInfo envIdInfo = parseScopedId(cseRecord.envId, cseRecord.orgIdentifier, cseRecord.projectIdentifier);
          if (envIdInfo != null) {
            String[] uniqueIds = lookupEnvUniqueId(cseRecord.accountIdentifier, envIdInfo.orgIdentifier,
                envIdInfo.projectIdentifier, envIdInfo.identifier);
            if (uniqueIds != null) {
              envUniqueId = uniqueIds[0];
              envParentUniqueId = uniqueIds[1];
            } else {
              // Environment entity not found in DB - use sentinel to avoid re-processing
              log.info(DEBUG_MESSAGE + "Environment not found for record {}, env_id: {}. Marking as UNKNOWN.",
                  cseRecord.id, cseRecord.envId);
              envUniqueId = UNKNOWN_UNIQUE_ID;
              envParentUniqueId = UNKNOWN_UNIQUE_ID;
            }
          } else {
            // Unable to parse env_id - use sentinel
            log.info(DEBUG_MESSAGE + "Unable to parse env_id for record {}: {}. Marking as UNKNOWN.", cseRecord.id,
                cseRecord.envId);
            envUniqueId = UNKNOWN_UNIQUE_ID;
            envParentUniqueId = UNKNOWN_UNIQUE_ID;
          }
        }
      }

      // Resolve infra unique IDs if infra_id needs backfill
      if (cseRecord.infraId != null) {
        if (isOrphaned) {
          // No scope info available - use sentinel to avoid re-processing
          infraUniqueId = UNKNOWN_UNIQUE_ID;
          infraParentUniqueId = UNKNOWN_UNIQUE_ID;
        } else if (isEmpty(cseRecord.rawEnvId)) {
          // Cannot look up infrastructure without env_id
          log.info(
              DEBUG_MESSAGE + "No env_id available for infra lookup on record {}, infra_id: {}. Marking as UNKNOWN.",
              cseRecord.id, cseRecord.infraId);
          infraUniqueId = UNKNOWN_UNIQUE_ID;
          infraParentUniqueId = UNKNOWN_UNIQUE_ID;
        } else {
          // Infrastructure inherits scope from its parent environment.
          // infra_id is always a raw identifier (no account./org. prefix unlike service and env).
          // env_id may have scope prefix (account. or org.) — parse it to determine the infra's scope
          // and strip it because infrastructures.env_identifier stores the raw identifier.
          ScopedIdInfo envIdInfo =
              parseScopedId(cseRecord.rawEnvId, cseRecord.orgIdentifier, cseRecord.projectIdentifier);
          String resolvedEnvId = envIdInfo != null ? envIdInfo.identifier : cseRecord.rawEnvId;
          String infraOrgId = envIdInfo != null ? envIdInfo.orgIdentifier : cseRecord.orgIdentifier;
          String infraProjectId = envIdInfo != null ? envIdInfo.projectIdentifier : cseRecord.projectIdentifier;
          String[] uniqueIds = lookupInfraUniqueId(
              cseRecord.accountIdentifier, infraOrgId, infraProjectId, resolvedEnvId, cseRecord.infraId);
          if (uniqueIds != null) {
            infraUniqueId = uniqueIds[0];
            infraParentUniqueId = uniqueIds[1];
          } else {
            // Infrastructure entity not found in DB - use sentinel to avoid re-processing
            log.info(
                DEBUG_MESSAGE + "Infrastructure not found for record {}, infra_id: {}, env_id: {}. Marking as UNKNOWN.",
                cseRecord.id, cseRecord.infraId, cseRecord.rawEnvId);
            infraUniqueId = UNKNOWN_UNIQUE_ID;
            infraParentUniqueId = UNKNOWN_UNIQUE_ID;
          }
        }
      }

      boolean hasUpdate = serviceUniqueId != null || envUniqueId != null || infraUniqueId != null;
      if (hasUpdate) {
        tsUpdates.add(new TimescaleUpdateInfo(cseRecord.id, serviceUniqueId, serviceParentUniqueId, envUniqueId,
            envParentUniqueId, infraUniqueId, infraParentUniqueId));
        mongoUpdates.add(new MongoUpdateInfo(cseRecord.id, serviceUniqueId, serviceParentUniqueId, envUniqueId,
            envParentUniqueId, infraUniqueId, infraParentUniqueId));
        updatedCount++;
      }
    }

    // Batch update TimescaleDB first — if this fails, skip MongoDB to keep them in sync
    if (!tsUpdates.isEmpty()) {
      if (!updateTimescaleDBBatch(tsUpdates)) {
        throw new RuntimeException("TimescaleDB batch update failed");
      }
    }

    // Batch update MongoDB
    if (!mongoUpdates.isEmpty()) {
      updateMongoDBRecordsBatch(mongoUpdates);
    }

    return updatedCount;
  }

  /**
   * Parses a scoped identifier to extract the identifier and determine the scope.
   * - account.{id} -&gt; account-scoped (org=null, project=null)
   * - org.{id} -&gt; org-scoped (project=null)
   * - {id} -&gt; project-scoped
   */
  private ScopedIdInfo parseScopedId(String scopedId, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(scopedId)) {
      return null;
    }

    if (scopedId.startsWith("account.")) {
      return new ScopedIdInfo(scopedId.substring(8), null, null);
    } else if (scopedId.startsWith("org.")) {
      return new ScopedIdInfo(scopedId.substring(4), orgIdentifier, null);
    } else {
      return new ScopedIdInfo(scopedId, orgIdentifier, projectIdentifier);
    }
  }

  private String[] lookupServiceUniqueId(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    String cacheKey = buildCacheKey("service", accountId, orgIdentifier, projectIdentifier, identifier);
    String[] cachedResult = serviceUniqueIdCache.getIfPresent(cacheKey);
    if (cachedResult != null) {
      return cachedResult;
    }

    try {
      var query = dslContext.select(SERVICES.ID, SERVICES.UNIQUE_ID, SERVICES.PARENT_UNIQUE_ID)
                      .from(SERVICES)
                      .where(SERVICES.ACCOUNT_ID.eq(accountId))
                      .and(SERVICES.IDENTIFIER.eq(identifier));

      if (orgIdentifier != null && projectIdentifier != null) {
        query =
            query.and(SERVICES.ORG_IDENTIFIER.eq(orgIdentifier)).and(SERVICES.PROJECT_IDENTIFIER.eq(projectIdentifier));
      } else if (orgIdentifier != null) {
        query = query.and(SERVICES.ORG_IDENTIFIER.eq(orgIdentifier)).and(SERVICES.PROJECT_IDENTIFIER.isNull());
      } else {
        query = query.and(SERVICES.ORG_IDENTIFIER.isNull()).and(SERVICES.PROJECT_IDENTIFIER.isNull());
      }

      Record record = query.orderBy(SERVICES.DELETED.asc().nullsFirst()).limit(1).fetchOne();

      if (record != null) {
        String uniqueId = record.get(SERVICES.UNIQUE_ID);
        String parentUniqueId = record.get(SERVICES.PARENT_UNIQUE_ID);
        if (uniqueId == null) {
          uniqueId = generateUuid();
          if (parentUniqueId == null) {
            parentUniqueId = UNKNOWN_UNIQUE_ID;
          }
          String rowId = record.get(SERVICES.ID);
          dslContext.update(SERVICES)
              .set(SERVICES.UNIQUE_ID, uniqueId)
              .set(SERVICES.PARENT_UNIQUE_ID, parentUniqueId)
              .where(SERVICES.ID.eq(rowId))
              .execute();
          log.info(DEBUG_MESSAGE + "Generated unique_id for service row id={}, unique_id={}", rowId, uniqueId);
        }
        String[] result = new String[] {uniqueId, parentUniqueId};
        serviceUniqueIdCache.put(cacheKey, result);
        return result;
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error looking up service unique_id", e);
    }
    return null;
  }

  private String[] lookupEnvUniqueId(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    String cacheKey = buildCacheKey("env", accountId, orgIdentifier, projectIdentifier, identifier);
    String[] cachedResult = envUniqueIdCache.getIfPresent(cacheKey);
    if (cachedResult != null) {
      return cachedResult;
    }

    try {
      var query = dslContext.select(ENVIRONMENTS.ID, ENVIRONMENTS.UNIQUE_ID, ENVIRONMENTS.PARENT_UNIQUE_ID)
                      .from(ENVIRONMENTS)
                      .where(ENVIRONMENTS.ACCOUNT_ID.eq(accountId))
                      .and(ENVIRONMENTS.IDENTIFIER.eq(identifier));

      if (orgIdentifier != null && projectIdentifier != null) {
        query = query.and(ENVIRONMENTS.ORG_IDENTIFIER.eq(orgIdentifier))
                    .and(ENVIRONMENTS.PROJECT_IDENTIFIER.eq(projectIdentifier));
      } else if (orgIdentifier != null) {
        query = query.and(ENVIRONMENTS.ORG_IDENTIFIER.eq(orgIdentifier)).and(ENVIRONMENTS.PROJECT_IDENTIFIER.isNull());
      } else {
        query = query.and(ENVIRONMENTS.ORG_IDENTIFIER.isNull()).and(ENVIRONMENTS.PROJECT_IDENTIFIER.isNull());
      }

      Record record = query.orderBy(ENVIRONMENTS.DELETED.asc().nullsFirst()).limit(1).fetchOne();

      if (record != null) {
        String uniqueId = record.get(ENVIRONMENTS.UNIQUE_ID);
        String parentUniqueId = record.get(ENVIRONMENTS.PARENT_UNIQUE_ID);
        if (uniqueId == null) {
          uniqueId = generateUuid();
          if (parentUniqueId == null) {
            parentUniqueId = UNKNOWN_UNIQUE_ID;
          }
          String rowId = record.get(ENVIRONMENTS.ID);
          dslContext.update(ENVIRONMENTS)
              .set(ENVIRONMENTS.UNIQUE_ID, uniqueId)
              .set(ENVIRONMENTS.PARENT_UNIQUE_ID, parentUniqueId)
              .where(ENVIRONMENTS.ID.eq(rowId))
              .execute();
          log.info(DEBUG_MESSAGE + "Generated unique_id for environment row id={}, unique_id={}", rowId, uniqueId);
        }
        String[] result = new String[] {uniqueId, parentUniqueId};
        envUniqueIdCache.put(cacheKey, result);
        return result;
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error looking up env unique_id", e);
    }
    return null;
  }

  private String[] lookupInfraUniqueId(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String identifier) {
    String cacheKey =
        buildCacheKey("infra", accountId, orgIdentifier, projectIdentifier, envIdentifier + "|" + identifier);
    String[] cachedResult = infraUniqueIdCache.getIfPresent(cacheKey);
    if (cachedResult != null) {
      return cachedResult;
    }

    try {
      var query = dslContext.select(INFRASTRUCTURES.ID, INFRASTRUCTURES.UNIQUE_ID, INFRASTRUCTURES.PARENT_UNIQUE_ID)
                      .from(INFRASTRUCTURES)
                      .where(INFRASTRUCTURES.ACCOUNT_ID.eq(accountId))
                      .and(INFRASTRUCTURES.IDENTIFIER.eq(identifier))
                      .and(INFRASTRUCTURES.ENV_IDENTIFIER.eq(envIdentifier));

      if (orgIdentifier != null && projectIdentifier != null) {
        query = query.and(INFRASTRUCTURES.ORG_IDENTIFIER.eq(orgIdentifier))
                    .and(INFRASTRUCTURES.PROJECT_IDENTIFIER.eq(projectIdentifier));
      } else if (orgIdentifier != null) {
        query = query.and(INFRASTRUCTURES.ORG_IDENTIFIER.eq(orgIdentifier))
                    .and(INFRASTRUCTURES.PROJECT_IDENTIFIER.isNull());
      } else {
        query = query.and(INFRASTRUCTURES.ORG_IDENTIFIER.isNull()).and(INFRASTRUCTURES.PROJECT_IDENTIFIER.isNull());
      }

      Record record = query.orderBy(INFRASTRUCTURES.DELETED.asc().nullsFirst()).limit(1).fetchOne();

      if (record != null) {
        String uniqueId = record.get(INFRASTRUCTURES.UNIQUE_ID);
        String parentUniqueId = record.get(INFRASTRUCTURES.PARENT_UNIQUE_ID);
        if (uniqueId == null) {
          uniqueId = generateUuid();
          if (parentUniqueId == null) {
            parentUniqueId = UNKNOWN_UNIQUE_ID;
          }
          String rowId = record.get(INFRASTRUCTURES.ID);
          dslContext.update(INFRASTRUCTURES)
              .set(INFRASTRUCTURES.UNIQUE_ID, uniqueId)
              .set(INFRASTRUCTURES.PARENT_UNIQUE_ID, parentUniqueId)
              .where(INFRASTRUCTURES.ID.eq(rowId))
              .execute();
          log.info(DEBUG_MESSAGE + "Generated unique_id for infrastructure row id={}, unique_id={}", rowId, uniqueId);
        }
        String[] result = new String[] {uniqueId, parentUniqueId};
        infraUniqueIdCache.put(cacheKey, result);
        return result;
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error looking up infra unique_id", e);
    }
    return null;
  }

  private String buildCacheKey(
      String prefix, String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    return prefix + "|" + accountId + "|" + (orgIdentifier != null ? orgIdentifier : "") + "|"
        + (projectIdentifier != null ? projectIdentifier : "") + "|" + identifier;
  }

  private boolean updateTimescaleDBBatch(List<TimescaleUpdateInfo> updates) {
    try {
      List<org.jooq.Query> queries = new ArrayList<>();
      for (TimescaleUpdateInfo update : updates) {
        var step = dslContext.update(CD_STAGE_EXECUTION);
        org.jooq.UpdateSetMoreStep<?> setStep = null;
        if (update.serviceUniqueId != null) {
          setStep = step.set(CD_STAGE_EXECUTION_SERVICE_UNIQUE_ID, update.serviceUniqueId)
                        .set(CD_STAGE_EXECUTION_SERVICE_PARENT_UNIQUE_ID, update.serviceParentUniqueId);
        }
        if (update.envUniqueId != null) {
          setStep = (setStep != null ? setStep : step)
                        .set(CD_STAGE_EXECUTION_ENV_UNIQUE_ID, update.envUniqueId)
                        .set(CD_STAGE_EXECUTION_ENV_PARENT_UNIQUE_ID, update.envParentUniqueId);
        }
        if (update.infraUniqueId != null) {
          setStep = (setStep != null ? setStep : step)
                        .set(CD_STAGE_EXECUTION_INFRA_UNIQUE_ID, update.infraUniqueId)
                        .set(CD_STAGE_EXECUTION_INFRA_PARENT_UNIQUE_ID, update.infraParentUniqueId);
        }
        if (setStep != null) {
          queries.add(setStep.where(CD_STAGE_EXECUTION_ID.eq(update.id)));
        }
      }

      if (!queries.isEmpty()) {
        dslContext.batch(queries).execute();
      }
      return true;
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error batch updating TimescaleDB records", e);
      return false;
    }
  }
  // Retry is essential here because this method runs after TSDB has already been committed.
  // Once TSDB unique_ids are set, those records won't appear in fetchBatchRecords again (IS NULL no longer matches),
  // so the outer retry loop in runMigration cannot recover from a MongoDB failure here.
  // A silent failure would leave MongoDB permanently out of sync with TSDB.
  private void updateMongoDBRecordsBatch(List<MongoUpdateInfo> mongoUpdates) {
    for (int attempt = 1; attempt <= MONGO_RETRY_COUNT; attempt++) {
      try {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, StageExecutionInfo.class);

        for (MongoUpdateInfo updateInfo : mongoUpdates) {
          Query query =
              new Query(Criteria.where(StageExecutionInfoKeys.stageExecutionId).is(updateInfo.stageExecutionId));
          Update update = new Update();
          if (updateInfo.serviceUniqueId != null) {
            update.set(StageExecutionInfoKeys.serviceUniqueId, updateInfo.serviceUniqueId);
            update.set(StageExecutionInfoKeys.serviceParentUniqueId, updateInfo.serviceParentUniqueId);
          }
          if (updateInfo.envUniqueId != null) {
            update.set(StageExecutionInfoKeys.envUniqueId, updateInfo.envUniqueId);
            update.set(StageExecutionInfoKeys.envParentUniqueId, updateInfo.envParentUniqueId);
          }
          if (updateInfo.infraUniqueId != null) {
            update.set(StageExecutionInfoKeys.infraUniqueId, updateInfo.infraUniqueId);
            update.set(StageExecutionInfoKeys.infraParentUniqueId, updateInfo.infraParentUniqueId);
          }
          bulkOps.updateOne(query, update);
        }

        bulkOps.execute();
        return;
      } catch (Exception e) {
        log.error(
            DEBUG_MESSAGE + "Error batch updating MongoDB records (attempt {}/{})", attempt, MONGO_RETRY_COUNT, e);
        if (attempt == MONGO_RETRY_COUNT) {
          throw new RuntimeException("MongoDB batch update failed after " + MONGO_RETRY_COUNT + " attempts. "
                  + "TSDB was already updated — these records will not be re-fetched. "
                  + "Manual reconciliation may be needed.",
              e);
        }
        try {
          Thread.sleep(MONGO_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted during MongoDB retry", ie);
        }
      }
    }
  }

  private record CdStageExecutionRecord(String id, String serviceId, String envId, String infraId, String rawEnvId,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {}

  private record ScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {}

  private record ScopedIdInfo(String identifier, String orgIdentifier, String projectIdentifier) {}

  private record TimescaleUpdateInfo(String id, String serviceUniqueId, String serviceParentUniqueId,
      String envUniqueId, String envParentUniqueId, String infraUniqueId, String infraParentUniqueId) {}

  private record MongoUpdateInfo(String stageExecutionId, String serviceUniqueId, String serviceParentUniqueId,
      String envUniqueId, String envParentUniqueId, String infraUniqueId, String infraParentUniqueId) {}
}
