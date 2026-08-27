/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.projectmovement.mongo;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.persistence.UniqueIdAccess.UNIQUE_ID_KEY;
import static io.harness.projectmovement.mongo.FieldNameHelper.isFieldSupported;
import static io.harness.projectmovement.mongo.FieldNameHelper.readNestedFieldValue;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.util.Map.of;
import static java.util.Objects.nonNull;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity.ExecutionExpressionUsagesEntityKeys;
import io.harness.engine.expressions.usages.beans.ExpressionUsagesEntity;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.BlockExecutionMetadata;
import io.harness.execution.gitmetadata.PipelineExecutionGitMetadata;
import io.harness.execution.step.StepExecutionEntity;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.IndexManager;
import io.harness.mongo.MongoConfig;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.store.Store;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.projectmovement.mongo.PipelineUniqueIdParentIdMigrationStatus.PipelineUniqueIdParentIdMigrationStatusKeys;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;

import com.google.inject.Inject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.result.DeleteResult;
import dev.morphia.Morphia;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mapping.model.MappingInstantiationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AddUniqueIdParentIdToEntitiesTask implements Runnable {
  private static final String PARENT_UNIQUE_ID_KEY = "parentUniqueId";
  private static final String ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX = "orphan_";
  private static final String PIPELINE_ENTITIES_MIGRATION_LOG = "[PipelineAddUniqueIdAndParentUniqueIdToEntitiesTask]:";
  private static final int BATCH_SIZE =
      Integer.parseInt(System.getenv().getOrDefault("PROJECT_MIGRATION_MONGO_MIGRATION_BATCH_SIZE", "500"));
  private static final String LOCK_NAME_PREFIX = "PipelineMongoMigrationTaskLock";
  private static final long SLEEP_DELAY_MS =
      Long.parseLong(System.getenv().getOrDefault("PROJECT_MIGRATION_MONGO_MIGRATION_SLEEP_DELAY_MS", "2000"));

  private static final String ORG_ID_KEY = "orgIdKey";
  private static final String PROJECT_ID_KEY = "projectIdKey";

  private static final String PARENT_UNIQUE_ID_FIELD_NAME = "parentUniqueIdFieldName";
  private static final String ID_FIELD_NAME = "idFieldName";
  private static final String ACCOUNT_IDENTIFIER_FIELD_NAME = "accountIdentifierFieldName";
  private static final String ORG_IDENTIFIER_FIELD_NAME = "orgIdentifierFieldName";
  private static final String PROJECT_IDENTIFIER_FIELD_NAME = "projectIdentifierFieldName";
  private Map<String, String> scopeEntityUniqueIdMap;

  private final MongoTemplate mongoTemplate;
  private final IndexManager indexManager;
  private final HPersistence persistence;
  private final MongoConfig mongoConfig;
  private final ScopeInfoClient scopeInfoClient;
  private final PersistentLocker persistentLocker;

  public static final Map<Class<? extends UniqueIdAware>, Map<String, String>> entityWithOrgProjectKeysMap =
      Map.ofEntries(
          Map.entry(InputSetEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(ApprovalInstance.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(BlockExecutionMetadata.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(TriggerEventHistory.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(PipelineMetadataV2.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(NGTriggerEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(PipelineEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(ExpressionUsagesEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(PipelineExecutionSummaryEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(StepExecutionEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(EventListenerStepInstance.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY,
                  UNIQUE_ID_KEY, NGCommonEntityConstants.MONGODB_ID)),
          Map.entry(PipelineExecutionGitMetadata.class,
              Map.of(
                  ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)));

  public static Map<Object, List<Map<String, String>>> getEntitiesWithNestedParentUniqueId() {
    Map<Object, List<Map<String, String>>> entities = new HashMap<>();
    entities.put(ExecutionExpressionUsagesEntity.class,
        List.of(of(ID_FIELD_NAME, ExecutionExpressionUsagesEntityKeys.uuid, PARENT_UNIQUE_ID_FIELD_NAME,
                    ExecutionExpressionUsagesEntityKeys.parentUniqueId, ACCOUNT_IDENTIFIER_FIELD_NAME,
                    ExecutionExpressionUsagesEntityKeys.accountId, ORG_IDENTIFIER_FIELD_NAME,
                    ExecutionExpressionUsagesEntityKeys.orgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME,
                    ExecutionExpressionUsagesEntityKeys.projectIdentifier),
            of(ID_FIELD_NAME, ExecutionExpressionUsagesEntityKeys.uuid, PARENT_UNIQUE_ID_FIELD_NAME,
                ExecutionExpressionUsagesEntityKeys.parentUniqueId, ACCOUNT_IDENTIFIER_FIELD_NAME,
                ExecutionExpressionUsagesEntityKeys.accountId, ORG_IDENTIFIER_FIELD_NAME,
                ExecutionExpressionUsagesEntityKeys.orgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME,
                ExecutionExpressionUsagesEntityKeys.projectIdentifier)));

    return entities;
  }

  @Inject
  public AddUniqueIdParentIdToEntitiesTask(MongoTemplate mongoTemplate, IndexManager indexManager,
      HPersistence persistence, MongoConfig mongoConfig, ScopeInfoClient scopeInfoClient,
      PersistentLocker persistentLocker) {
    this.mongoTemplate = mongoTemplate;
    this.indexManager = indexManager;
    this.persistence = persistence;
    this.mongoConfig = mongoConfig;
    this.scopeInfoClient = scopeInfoClient;
    this.persistentLocker = persistentLocker;
    this.scopeEntityUniqueIdMap = new HashMap<>();
  }

  public static Map<Class<? extends UniqueIdAware>, Map<String, String>> entityWithOrgProjectKeysMap() {
    return entityWithOrgProjectKeysMap;
  }

  @Override
  public void run() {
    log.info("{} starting...", PIPELINE_ENTITIES_MIGRATION_LOG);

    if (getMaintenanceFlag()) {
      log.warn("[{}]: Service is going in maintenance mode. Thread going to sleep for the pipeline mongo migration job",
          PIPELINE_ENTITIES_MIGRATION_LOG);
      return;
    }

    handleRollbacks();

    for (Map.Entry<Class<? extends UniqueIdAware>, Map<String, String>> entityMapEntry :
        entityWithOrgProjectKeysMap.entrySet()) {
      Map<String, String> fieldMap = entityMapEntry.getValue();
      String orgIdentifierFieldName = fieldMap.get(ORG_ID_KEY);
      String projectIdentifierFieldName = fieldMap.get(PROJECT_ID_KEY);
      String uniqueIdField = fieldMap.getOrDefault(UNIQUE_ID_KEY, UNIQUE_ID_KEY);

      Class<? extends UniqueIdAware> clazz = entityMapEntry.getKey();
      final String typeAliasName = getTypeAliasValueOrNameForClass(clazz);
      PipelineUniqueIdParentIdMigrationStatus foundEntity = mongoTemplate.findOne(
          new Query(Criteria.where(PipelineUniqueIdParentIdMigrationStatusKeys.entityClassName).is(typeAliasName)),
          PipelineUniqueIdParentIdMigrationStatus.class);
      if (foundEntity == null) {
        foundEntity = PipelineUniqueIdParentIdMigrationStatus.builder()
                          .entityClassName(typeAliasName)
                          .parentIdMigrationCompleted(Boolean.FALSE)
                          .uniqueIdMigrationCompleted(Boolean.FALSE)
                          .build();
      }

      if (TRUE.equals(foundEntity.getUniqueIdMigrationCompleted())) {
        log.info("{} job for uniqueId on Entity Type: [{}] already completed.", PIPELINE_ENTITIES_MIGRATION_LOG,
            clazz.getSimpleName());
      } else {
        performUniqueIdMigrationTask(foundEntity, clazz, uniqueIdField);
      }

      if (TRUE.equals(foundEntity.getParentIdMigrationCompleted())) {
        log.info("{} job for parentId on Entity Type: [{}] already completed.", PIPELINE_ENTITIES_MIGRATION_LOG,
            clazz.getSimpleName());
      } else {
        performParentIdMigrationTask(foundEntity, clazz, orgIdentifierFieldName, projectIdentifierFieldName);
      }

      if (TRUE.equals(foundEntity.getIndexCreationCompleted())) {
        log.info("{} job for index creation on Entity Type: [{}] already completed.", PIPELINE_ENTITIES_MIGRATION_LOG,
            clazz.getSimpleName());
      } else {
        performMissingIndexCreation(foundEntity, clazz);
      }
    }

    // Migration for parent unique id present at nested level
    Map<Object, List<Map<String, String>>> entities = getEntitiesWithNestedParentUniqueId();

    for (Map.Entry<Object, List<Map<String, String>>> entity : entities.entrySet()) {
      PipelineUniqueIdParentIdMigrationStatus foundEntity =
          mongoTemplate.findOne(new Query(Criteria.where(PipelineUniqueIdParentIdMigrationStatusKeys.entityClassName)
                                              .is(((Class) entity.getKey()).getName())),
              PipelineUniqueIdParentIdMigrationStatus.class);
      if (foundEntity == null) {
        foundEntity = PipelineUniqueIdParentIdMigrationStatus.builder()
                          .entityClassName(((Class) entity.getKey()).getName())
                          .parentIdMigrationCompleted(Boolean.FALSE)
                          .build();
      }
      log.info(format(
          "%s starting job for nested parentId on entity [%s]", PIPELINE_ENTITIES_MIGRATION_LOG, entity.getKey()));
      if (TRUE.equals(foundEntity.getParentIdMigrationCompleted())) {
        log.info(format("%s job for parentId on entity [%s] already completed successfully.",
            PIPELINE_ENTITIES_MIGRATION_LOG, entity.getKey()));
      } else {
        performNestedParentUniqueIdMigration(entity, foundEntity);
      }

      if (TRUE.equals(foundEntity.getIndexCreationCompleted())) {
        log.info(format("%s job for index creation on Entity Type: [%s] already completed.",
            PIPELINE_ENTITIES_MIGRATION_LOG, entity.getKey()));
      } else {
        performMissingIndexCreation(foundEntity, (Class<? extends UniqueIdAware>) entity.getKey());
      }
    }
  }

  private void performMissingIndexCreation(
      PipelineUniqueIdParentIdMigrationStatus foundEntity, final Class<? extends UniqueIdAware> clazz) {
    Morphia morphia = new Morphia();
    morphia.map(clazz);

    Store store = null;
    if (Objects.nonNull(mongoConfig.getAliasDBName())) {
      store = Store.builder().name(mongoConfig.getAliasDBName()).build();
    }

    try {
      indexManager.ensureIndexes(AUTO, persistence.getDatastore(clazz), morphia, store);
      foundEntity.setIndexCreationCompleted(TRUE);
      mongoTemplate.save(foundEntity);
      log.info(format("%s job Succeeded for index creation on Entity Type [%s]", PIPELINE_ENTITIES_MIGRATION_LOG,
          clazz.getSimpleName()));
    } catch (Exception e) {
      log.error(format("%s job failed for index creation on Entity Type [%s]", PIPELINE_ENTITIES_MIGRATION_LOG,
                    clazz.getSimpleName()),
          e);
    }
  }

  private void performUniqueIdMigrationTask(PipelineUniqueIdParentIdMigrationStatus migrationStatusEntity,
      final Class<? extends UniqueIdAware> clazz, final String uniqueIdField) {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Mongo DB entity [%s] during uniqueId migration task",
            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        return;
      }
      log.info(
          "{} Starting uniqueId migration for Entity: [{}]", PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName());

      int migratedCounter = 0;
      int batchSizeCounter = 0;
      int toUpdateCounter = 0;
      int skippedCounter = 0;

      try {
        String collectionName = mongoTemplate.getCollectionName(clazz);
        List<UpdateOneModel<Document>> bulkUpdates = new ArrayList<>();
        BulkWriteOptions options = new BulkWriteOptions().ordered(false);
        //        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
        // Use raw MongoDB cursor to avoid deserialization
        try (MongoCursor<Document> cursor = mongoTemplate.getCollection(collectionName)
                                                .find()
                                                .limit(NO_LIMIT)
                                                .maxTime(MAX_VALUE, TimeUnit.MILLISECONDS)
                                                .noCursorTimeout(true)
                                                .iterator()) {
          // last document for debugging nested interface/abstract class fields or corrupted records that cannot be
          // cast.
          Document lastDoc = null;
          while (cursor.hasNext()) {
            try {
              Document doc = cursor.next();
              lastDoc = doc;
              String uniqueId = doc.getString(uniqueIdField);

              if (isEmpty(uniqueId)) {
                Object idObj = doc.get("_id");
                if (idObj != null) {
                  toUpdateCounter++;

                  // Create the update operation with Bson
                  BsonDocument update = new BsonDocument(
                      "$set", new BsonDocument(uniqueIdField, new BsonString(UUIDGenerator.generateUuid())));

                  // Dynamically create the filter based on the type of _id
                  BsonDocument filter;
                  if (idObj instanceof String) {
                    filter = new BsonDocument("_id", new BsonString((String) idObj)); // _id is a String
                  } else if (idObj instanceof ObjectId) {
                    filter = new BsonDocument("_id", new BsonObjectId((ObjectId) idObj)); // _id is an ObjectId
                  } else {
                    // If it's neither, we'll just skip it for now (you can add error handling here)
                    log.debug("Unexpected _id type, skipping document: " + doc.toJson());
                    skippedCounter++;
                    continue;
                  }
                  batchSizeCounter++;
                  // Add to bulk update list
                  bulkUpdates.add(new UpdateOneModel<>(filter, update));

                  // If batch size reaches the limit, execute the bulk operation
                  if (batchSizeCounter == BATCH_SIZE) {
                    // Execute bulk write operation
                    migratedCounter +=
                        mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
                    bulkUpdates.clear(); // Clear the list for the next batch
                    Thread.sleep(SLEEP_DELAY_MS);
                    batchSizeCounter = 0;
                  }
                }
              }
            } catch (Exception exc) {
              log.error(format("%s job for uniqueId migration on Entity: [%s], encountered error processing document: "
                                + "[%s], skipping",
                            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(),
                            lastDoc != null ? lastDoc.toJson() : "null"),
                  exc);
              skippedCounter++;
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter +=
                mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
          }
        } catch (Exception e) {
          log.error(format("%s job for uniqueId failed to iterate over entities of Entity Type [%s]",
                        PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              e);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s job for uniqueId failed on Entity Type [%s]", PIPELINE_ENTITIES_MIGRATION_LOG,
                      clazz.getSimpleName()),
            exc);
        return;
      }

      if (toUpdateCounter == migratedCounter) {
        migrationStatusEntity.setUniqueIdMigrationCompleted(TRUE);
        log.info(format("%s job on entity [%s] for uniqueId Succeeded. Documents to Update and Successful: [%d], "
                + "Skipped(Failed or Invalid Entities): [%d]",
            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, skippedCounter));
      } else {
        log.warn(format("%s job failed on entity [%s] for uniqueId. Documents to Update: [%d], Successful: [%d], "
                + "Failed: [%d], Skipped(Failed or Invalid Entities): [%d]",
            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
            toUpdateCounter - migratedCounter, skippedCounter));
      }
      mongoTemplate.save(migrationStatusEntity);
    }
  }

  private void performParentIdMigrationTask(PipelineUniqueIdParentIdMigrationStatus foundEntity,
      final Class<? extends UniqueIdAware> clazz, final String orgIdentifierFieldName,
      final String projectIdentifierFieldName) {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info("{} failed to acquire lock for mongo DB entity [{}] during parentUniqueId migration task",
            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName());
        return;
      }
      log.info("{} Starting parentUniqueId migration for Entity: [{}]", PIPELINE_ENTITIES_MIGRATION_LOG,
          clazz.getSimpleName());
      int migratedCounter = 0;
      int toUpdateCounter = 0;
      int batchSizeCounter = 0;
      int skippedCounter = 0;
      int orphanEntityCounter = 0;
      final String LOCAL_MAP_DELIMITER = "|";

      try {
        final Map<String, String> scopeEntityUniqueIdMap = new HashMap<>();

        String collectionName = mongoTemplate.getCollectionName(clazz);
        List<UpdateOneModel<Document>> bulkUpdates = new ArrayList<>();
        BulkWriteOptions options = new BulkWriteOptions().ordered(false);
        //        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);

        // Use raw MongoDB cursor to avoid deserialization
        try (MongoCursor<Document> cursor = mongoTemplate.getCollection(collectionName)
                                                .find()
                                                .limit(NO_LIMIT)
                                                .maxTime(MAX_VALUE, TimeUnit.MILLISECONDS)
                                                .noCursorTimeout(true)
                                                .iterator()) {
          // last document for debugging nested interface/abstract class fields or corrupted records that cannot be
          // cast.
          Document lastDoc = null;
          while (cursor.hasNext()) {
            try {
              Document doc = cursor.next();
              lastDoc = doc;
              // Check if document has parentUniqueId, uniqueId and account fields
              String parentUniqueId = doc.getString(PARENT_UNIQUE_ID_KEY);
              String account = doc.getString(NGCommonEntityConstants.ACCOUNT_KEY);
              if (StringUtils.isEmpty(account)) {
                account = doc.getString(NGCommonEntityConstants.ACCOUNT_ID);
              }
              if (account == null) {
                // we skip this as this is stale document
                continue;
              }
              String org = doc.getString(orgIdentifierFieldName);
              String proj = doc.getString(projectIdentifierFieldName);
              String mapKey;
              if (isEmpty(parentUniqueId)) {
                toUpdateCounter++;
                if (isNotEmpty(org) && isNotEmpty(proj)) {
                  mapKey = account + LOCAL_MAP_DELIMITER + org + LOCAL_MAP_DELIMITER + proj;
                } else if (isNotEmpty(org)) {
                  mapKey = account + LOCAL_MAP_DELIMITER + org;
                } else {
                  mapKey = account;
                }

                String scopeUniqueId;
                if (scopeEntityUniqueIdMap.containsKey(mapKey)) {
                  scopeUniqueId = scopeEntityUniqueIdMap.get(mapKey);
                } else {
                  if (isNotEmpty(org) || isNotEmpty(proj)) {
                    ScopeInfo scopeInfo = null;
                    try {
                      scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(account, org, proj));
                    } catch (InvalidRequestException exception) {
                      // nothing to handle here, will mark entity as orphan
                    }
                    if (scopeInfo == null) {
                      scopeUniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
                      orphanEntityCounter++;
                    } else {
                      scopeUniqueId = scopeInfo.getUniqueId();
                    }
                  } else {
                    scopeUniqueId = account;
                  }
                }

                Object idObj = doc.get("_id");
                if (idObj != null && isNotEmpty(scopeUniqueId)) {
                  scopeEntityUniqueIdMap.put(mapKey, scopeUniqueId);
                  // Create query with the correct id type
                  BsonDocument update =
                      new BsonDocument("$set", new BsonDocument(PARENT_UNIQUE_ID_KEY, new BsonString(scopeUniqueId)));
                  // Dynamically create the filter based on the type of _id
                  BsonDocument filter;
                  if (idObj instanceof String) {
                    filter = new BsonDocument("_id", new BsonString((String) idObj)); // _id is a String
                  } else if (idObj instanceof ObjectId) {
                    filter = new BsonDocument("_id", new BsonObjectId((ObjectId) idObj)); // _id is an ObjectId
                  } else {
                    // If it's neither, we'll just skip it for now (you can add error handling here)
                    log.debug("Unexpected _id type, skipping document: " + doc.toJson());
                    skippedCounter++;
                    continue;
                  }

                  // non-scope entities update logic
                  batchSizeCounter++;
                  bulkUpdates.add(new UpdateOneModel<>(filter, update));
                  if (batchSizeCounter == BATCH_SIZE) {
                    // Execute bulk write operation
                    migratedCounter +=
                        mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
                    bulkUpdates.clear(); // Clear the list for the next batch
                    Thread.sleep(SLEEP_DELAY_MS);
                    batchSizeCounter = 0;
                  }
                }
              }
            } catch (MappingInstantiationException | IllegalArgumentException exc) {
              log.error(format("%s job for parentUniqueId migration on Entity: [%s], encountered non-supported "
                                + "typeAlias or wrong arguments, skipping entity document: [%s]",
                            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(),
                            lastDoc != null ? lastDoc.toJson() : "null"),
                  exc);
              skippedCounter++;
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter +=
                mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
          }
        } catch (Exception exc) {
          log.error(
              format("%s task failed to iterate over entities during parentUniqueId migration of Entity Type: [%s]",
                  PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              exc);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s task failed during parentUniqueId migration for Entity Type [%s]",
                      PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
            exc);
        return;
      }

      if (toUpdateCounter == migratedCounter) {
        foundEntity.setParentIdMigrationCompleted(TRUE);
        foundEntity.setOrphanEntityParentIdMigrationCompleted(TRUE);
        log.info(format("%s job on entity [%s] for parentUniqueId Succeeded. Documents to Update: [%d], Successful: "
                + "[%d], Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
            orphanEntityCounter, skippedCounter));
      } else {
        log.warn(format("%s job failed on entity [%s] for parentUniqueId. Documents to Update: [%d], Successful: [%d], "
                + "Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
            orphanEntityCounter, skippedCounter));
      }
      mongoTemplate.save(foundEntity);
    }
  }

  private String getTypeAliasValueOrNameForClass(Class<? extends UniqueIdAware> clazz) {
    if (clazz.isAnnotationPresent(TypeAlias.class)) {
      TypeAlias annotation = clazz.getAnnotation(TypeAlias.class);
      return annotation.value();
    }
    return clazz.getName();
  }

  private boolean classHasField(final Class<? extends UniqueIdAware> clazz, final String fieldName) {
    return Arrays.stream(clazz.getDeclaredFields())
        .map(Field::getName)
        .anyMatch(f -> nonNull(f) && f.equals(fieldName));
  }

  private boolean superclassHasField(final Class<? extends UniqueIdAware> clazz, final String fieldName) {
    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null) {
      return Arrays.stream(superClass.getDeclaredFields())
          .map(Field::getName)
          .anyMatch(f -> nonNull(f) && f.equals(fieldName));
    }
    return false;
  }
  private String getValueOfFieldInEntity(
      Class<? extends UniqueIdAware> clazz, final String fieldName, UniqueIdAware entity) {
    if (!classHasField(clazz, fieldName) && !superclassHasField(clazz, fieldName)) {
      return null;
    }
    String value = null;
    try {
      Field field = getField(clazz, fieldName);
      field.setAccessible(true);
      value = (String) field.get(entity);
    } catch (IllegalAccessException e) {
      log.warn(format("%s For EntityType: [%s], cannot get or access value for field: [%s]",
          PIPELINE_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), fieldName));
    }
    return value;
  }

  private static Field getField(Class<?> clazz, String fieldName) {
    Field field = null;
    try {
      field = clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = clazz.getSuperclass();
      if (superClass != null) {
        field = getField(superClass, fieldName);
      }
    }
    return field;
  }

  private void handleRollbacks() {
    try {
      Set<String> registeredEntities = new HashSet<>(getTypeAliasSet());
      registeredEntities.addAll(getTypeAliasSetForNestedEntity());
      List<PipelineUniqueIdParentIdMigrationStatus> pipelineUniqueIdParentIdMigrationStatusList =
          mongoTemplate.find(new Query(), PipelineUniqueIdParentIdMigrationStatus.class);
      List<String> entitiesToBeDeleted = new ArrayList<>();
      for (PipelineUniqueIdParentIdMigrationStatus pipelineUniqueIdParentIdMigrationStatus :
          pipelineUniqueIdParentIdMigrationStatusList) {
        String migratedEntityClassName = pipelineUniqueIdParentIdMigrationStatus.getEntityClassName();
        if (!registeredEntities.contains(migratedEntityClassName)) {
          log.info(String.format(
              "%s entity will be deleted in the mongo uniqueIdParentIdMigrationStatus.", migratedEntityClassName));
          entitiesToBeDeleted.add(migratedEntityClassName);
        }
      }
      if (isNotEmpty(entitiesToBeDeleted)) {
        Criteria criteria = new Criteria();
        criteria.and(PipelineUniqueIdParentIdMigrationStatusKeys.entityClassName).in(entitiesToBeDeleted);
        DeleteResult deleteResult =
            mongoTemplate.remove(new Query(criteria), PipelineUniqueIdParentIdMigrationStatus.class);
        log.info(String.format("%s job successfully deleted records %d in mongo uniqueIdParentIdMigrationStatus",
            PIPELINE_ENTITIES_MIGRATION_LOG, deleteResult.getDeletedCount()));
      }
    } catch (Exception exception) {
      log.error(String.format("%s job failed to handle rollback.", PIPELINE_ENTITIES_MIGRATION_LOG), exception);
    }
  }

  private Set<String> getTypeAliasSet() {
    return entityWithOrgProjectKeysMap.keySet()
        .stream()
        .map(this::getTypeAliasValueOrNameForClass)
        .collect(Collectors.toSet());
  }

  private Set<String> getTypeAliasSetForNestedEntity() {
    return getEntitiesWithNestedParentUniqueId()
        .keySet()
        .stream()
        .map(clazz -> ((Class<?>) clazz).getName())
        .collect(Collectors.toSet());
  }

  private void performNestedParentUniqueIdMigration(
      Map.Entry<Object, List<Map<String, String>>> entity, PipelineUniqueIdParentIdMigrationStatus foundEntity) {
    String idValue = null;
    int updatedCounter = 0;
    int migratedCounter = 0;
    int failedCounter = 0;
    try {
      Query documentQuery = new Query(new Criteria());
      BulkOperations bulkOperations =
          mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, (Class<?>) entity.getKey());
      try (Stream<?> stream = mongoTemplate.stream(
               documentQuery.limit(MongoConfig.NO_LIMIT).maxTimeMsec(MAX_VALUE), (Class<?>) entity.getKey())) {
        Iterator<?> iterator = stream.iterator();
        while (iterator.hasNext()) {
          try {
            Object document = iterator.next();
            for (Map<String, String> mapping : entity.getValue()) {
              if (!isFieldSupported(document, mapping.get(ID_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(PARENT_UNIQUE_ID_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(ACCOUNT_IDENTIFIER_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(ORG_IDENTIFIER_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(PROJECT_IDENTIFIER_FIELD_NAME))) {
                continue;
              }
              idValue = (String) readNestedFieldValue(document, mapping.get(ID_FIELD_NAME));
              String parentUniqueId = (String) readNestedFieldValue(document, mapping.get(PARENT_UNIQUE_ID_FIELD_NAME));

              if (isEmpty(parentUniqueId)) {
                String accountIdentifier =
                    (String) readNestedFieldValue(document, mapping.get(ACCOUNT_IDENTIFIER_FIELD_NAME));
                String orgIdentifier = (String) readNestedFieldValue(document, mapping.get(ORG_IDENTIFIER_FIELD_NAME));
                String projectIdentifier =
                    (String) readNestedFieldValue(document, mapping.get(PROJECT_IDENTIFIER_FIELD_NAME));

                parentUniqueId = getScopeUniqueIdFor(accountIdentifier, orgIdentifier, projectIdentifier);

                Update update = new Update().set(mapping.get(PARENT_UNIQUE_ID_FIELD_NAME), parentUniqueId);
                bulkOperations.updateOne(new Query(Criteria.where("_id").is(idValue)), update);
                updatedCounter++;
              }

              if (updatedCounter > BATCH_SIZE) {
                migratedCounter += bulkOperations.execute().getModifiedCount();
                bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, (Class<?>) entity.getKey());
                updatedCounter = 0;
              }
            }
          } catch (MappingInstantiationException | IllegalArgumentException exception) {
            log.info(format("%s ", PIPELINE_ENTITIES_MIGRATION_LOG), exception);
            failedCounter++;
          }
        }
        if (updatedCounter > 0) { // for the last remaining batch of entities
          migratedCounter += bulkOperations.execute().getModifiedCount();
        }
      } catch (Exception exception) {
        log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                          + "Skipped(Failed or Invalid Entities): [%d]",
                      PIPELINE_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter),
            exception);
        return;
      }
    } catch (Exception exception) {
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                        + "Skipped(Failed or Invalid Entities): [%d]",
                    PIPELINE_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter),
          exception);
      return;
    }

    if (failedCounter > 0) {
      foundEntity.setParentIdMigrationCompleted(FALSE);
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          PIPELINE_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter));
    } else {
      foundEntity.setParentIdMigrationCompleted(TRUE);
      log.info(format("%s job for parentId on entity [%s] Succeeded. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          PIPELINE_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter));
    }
    mongoTemplate.save(foundEntity);
  }

  private String getScopeUniqueIdFor(String account, String org, String proj) {
    String mapKey = null;
    String LOCAL_MAP_DELIMITER = "|";
    if (isNotEmpty(org) && isNotEmpty(proj)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org + LOCAL_MAP_DELIMITER + proj;
    } else if (isNotEmpty(org)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org;
    } else {
      mapKey = account;
    }

    String scopeUniqueId;
    if (scopeEntityUniqueIdMap.containsKey(mapKey)) {
      scopeUniqueId = scopeEntityUniqueIdMap.get(mapKey);
    } else {
      if (isNotEmpty(org) || isNotEmpty(proj)) {
        ScopeInfo scopeInfo = null;
        try {
          scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(account, org, proj));
        } catch (InvalidRequestException exception) {
          // nothing to handle here, will mark entity as orphan
        }
        if (scopeInfo == null) {
          scopeUniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
        } else {
          scopeUniqueId = scopeInfo.getUniqueId();
        }
      } else {
        scopeUniqueId = account;
      }
      scopeEntityUniqueIdMap.put(mapKey, scopeUniqueId);
    }
    return scopeUniqueId;
  }
}
