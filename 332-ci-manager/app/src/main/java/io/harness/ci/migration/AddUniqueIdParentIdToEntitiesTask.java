/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.util.Objects.nonNull;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIManagerUniqueIdParentIdMigrationStatus;
import io.harness.app.beans.entities.CIManagerUniqueIdParentIdMigrationStatus.CIManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.beans.ScopeInfo;
import io.harness.beans.steps.CIPipelineBaseline;
import io.harness.beans.steps.CIStageBaseline;
import io.harness.beans.steps.CIStepOptimizationState;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.IndexManager;
import io.harness.mongo.MongoConfig;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UniqueIdAccess;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.store.Store;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.inject.Inject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import dev.morphia.Morphia;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
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
@OwnedBy(HarnessTeam.CI)
public class AddUniqueIdParentIdToEntitiesTask implements Runnable {
  private static final String PARENT_UNIQUE_ID_KEY = "parentUniqueId";
  private static final String ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX = "orphan_";
  private static final String CI_MANAGER_ENTITIES_MIGRATION_LOG =
      "[AddUniqueIdAndParentUniqueIdToCIManagerEntitiesTask]:";

  private static final String ORG_KEY = "orgId";
  private static final String PROJECT_KEY = "projectId";
  private static final String LOCK_NAME_PREFIX = "CIMongoMigrationTaskLock";
  private static final long SLEEP_DELAY_MS =
      Long.parseLong(System.getenv().getOrDefault("PROJECT_MIGRATION_MONGO_MIGRATION_SLEEP_DELAY_MS", "2000"));

  private static final int BATCH_SIZE = 500;

  private final MongoTemplate mongoTemplate;
  private final IndexManager indexManager;
  private final HPersistence persistence;
  private final MongoConfig mongoConfig;
  private final ScopeInfoClient scopeInfoClient;
  private final PersistentLocker persistentLocker;

  private static final Map<Class<? extends UniqueIdAware>, List<String>> entityWithOrgProjectKeysMap =
      Map.ofEntries(Map.entry(CIPipelineBaseline.class, List.of(ORG_KEY, PROJECT_KEY)),
          Map.entry(CIStepOptimizationState.class, List.of(ORG_KEY, PROJECT_KEY)),
          Map.entry(CIStageBaseline.class, List.of(ORG_KEY, PROJECT_KEY)));

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
  }

  @Override
  public void run() {
    log.info(format("%s starting...", CI_MANAGER_ENTITIES_MIGRATION_LOG));

    for (Map.Entry<Class<? extends UniqueIdAware>, List<String>> entityMapEntry :
        entityWithOrgProjectKeysMap.entrySet()) {
      try {
        String orgIdentifierFieldName = null;
        String projectIdentifierFieldName = null;
        List<String> orgProjectKeysValue = entityMapEntry.getValue();
        if (isNotEmpty(orgProjectKeysValue)) {
          if (orgProjectKeysValue.size() == 2) {
            orgIdentifierFieldName = orgProjectKeysValue.get(0);
            projectIdentifierFieldName = orgProjectKeysValue.get(1);
          } else if (orgProjectKeysValue.size() == 1) {
            orgIdentifierFieldName = orgProjectKeysValue.get(0);
          }
        }

        Class<? extends UniqueIdAware> clazz = entityMapEntry.getKey();
        final String typeAliasName = getTypeAliasValueOrNameForClass(clazz);
        CIManagerUniqueIdParentIdMigrationStatus foundEntity = mongoTemplate.findOne(
            new Query(Criteria.where(CIManagerUniqueIdParentIdMigrationStatusKeys.entityClassName).is(typeAliasName)),
            CIManagerUniqueIdParentIdMigrationStatus.class);
        if (foundEntity == null) {
          foundEntity = CIManagerUniqueIdParentIdMigrationStatus.builder()
                            .entityClassName(typeAliasName)
                            .parentIdMigrationCompleted(Boolean.FALSE)
                            .uniqueIdMigrationCompleted(Boolean.FALSE)
                            .build();
        }

        if (TRUE.equals(foundEntity.getUniqueIdMigrationCompleted())) {
          log.info(format("%s job for uniqueId on Entity Type: [%s] already completed.",
              CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        } else {
          performUniqueIdMigrationTask(foundEntity, clazz);
        }

        if (TRUE.equals(foundEntity.getParentIdMigrationCompleted())) {
          log.info(format("%s job for parentId on Entity Type: [%s] already completed.",
              CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        } else {
          performParentIdMigrationTask(foundEntity, clazz, orgIdentifierFieldName, projectIdentifierFieldName);
        }

        if (TRUE.equals(foundEntity.getIndexCreationCompleted())) {
          log.info(format("%s job for index creation on Entity Type: [%s] already completed.",
              CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        } else {
          performMissingIndexCreation(foundEntity, clazz);
        }
      } catch (Exception e) {
        log.error(format("%s job failed on Entity Type [%s]", CI_MANAGER_ENTITIES_MIGRATION_LOG,
                      entityMapEntry.getKey().getSimpleName()),
            e);
      }
    }
  }

  private void performMissingIndexCreation(
      CIManagerUniqueIdParentIdMigrationStatus foundEntity, final Class<? extends UniqueIdAware> clazz) {
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
      log.info(format("%s job Succeeded for index creation on Entity Type [%s]", CI_MANAGER_ENTITIES_MIGRATION_LOG,
          clazz.getSimpleName()));
    } catch (Exception e) {
      log.error(format("%s job failed for index creation on Entity Type [%s]", CI_MANAGER_ENTITIES_MIGRATION_LOG,
                    clazz.getSimpleName()),
          e);
    }
  }

  private void performUniqueIdMigrationTask(
      CIManagerUniqueIdParentIdMigrationStatus migrationStatusEntity, final Class<? extends UniqueIdAware> clazz) {
    log.info(format(
        "%s Starting uniqueId migration for Entity: [%s]", CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));

    int migratedCounter = 0;
    int batchSizeCounter = 0;
    int toUpdateCounter = 0;
    int skippedCounter = 0;

    try {
      Query documentQuery = new Query(new Criteria());
      BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
      String idValue = null;
      try (Stream<? extends UniqueIdAware> stream =
               mongoTemplate.stream(documentQuery.limit(MongoConfig.NO_LIMIT).maxTimeMsec(MAX_VALUE), clazz)) {
        Iterator<? extends UniqueIdAware> iterator = stream.iterator();
        while (iterator.hasNext()) {
          try {
            UniqueIdAware entity = iterator.next();
            if (isEmpty(entity.getUniqueId())) {
              idValue = getValueOfFieldInEntity(clazz, NGCommonEntityConstants.ENTITY_ID_FIELD_NAME, entity);
              if (isEmpty(idValue)) {
                // multiple entities have 'uuid' field instead of 'id' field
                idValue = getValueOfFieldInEntity(clazz, NGCommonEntityConstants.UUID, entity);
              }
              if (isNotEmpty(idValue)) {
                toUpdateCounter++;
                batchSizeCounter++;
                Update update = new Update().set(UniqueIdAccess.UNIQUE_ID_KEY, UUIDGenerator.generateUuid());
                bulkOperations.updateOne(new Query(Criteria.where("_id").is(idValue)), update);
                if (batchSizeCounter == BATCH_SIZE) {
                  migratedCounter += bulkOperations.execute().getModifiedCount();
                  bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
                  batchSizeCounter = 0;
                }
              }
            }
          } catch (MappingInstantiationException | IllegalArgumentException exc) {
            log.info(format("%s job for uniqueId migration on Entity: [%s], encountered non-supported typeAlias or "
                             + "wrong arguments, skipping entity document",
                         CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
                exc);
            skippedCounter++;
          }
        }
        if (batchSizeCounter > 0) { // for the last remaining batch of entities
          migratedCounter += bulkOperations.execute().getModifiedCount();
        }
      } catch (Exception e) {
        log.error(format("%s job for uniqueId failed to iterate over entities of Entity Type [%s]",
                      CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
            e);
        return;
      }
    } catch (Exception exc) {
      log.error(format("%s job for uniqueId failed on Entity Type [%s]", CI_MANAGER_ENTITIES_MIGRATION_LOG,
                    clazz.getSimpleName()),
          exc);
      return;
    }

    if (toUpdateCounter == migratedCounter) {
      migrationStatusEntity.setUniqueIdMigrationCompleted(TRUE);
      log.info(format("%s job on entity [%s] for uniqueId Succeeded. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, skippedCounter));
    } else {
      log.warn(format("%s job failed on entity [%s] for uniqueId. Documents to Update: [%d], Successful: [%d], Failed: "
              + "[%d], Skipped(Failed or Invalid Entities): [%d]",
          CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
          toUpdateCounter - migratedCounter, skippedCounter));
    }
    mongoTemplate.save(migrationStatusEntity);
  }

  private void performParentIdMigrationTask(CIManagerUniqueIdParentIdMigrationStatus foundEntity,
      final Class<? extends UniqueIdAware> clazz, final String orgIdentifierFieldName,
      final String projectIdentifierFieldName) {
    performEntityParentUniqueIdMigrationTask(foundEntity, clazz, orgIdentifierFieldName, projectIdentifierFieldName);
  }

  private void performEntityParentUniqueIdMigrationTask(CIManagerUniqueIdParentIdMigrationStatus foundEntity,
      final Class<? extends UniqueIdAware> clazz, final String orgIdentifierFieldName,
      final String projectIdentifierFieldName) {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info("{} failed to acquire lock for mongo DB entity [{}] during parentUniqueId migration task",
            CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName());
        return;
      }
      log.info("{} Starting parentUniqueId migration for Entity: [{}]", CI_MANAGER_ENTITIES_MIGRATION_LOG,
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
              if (isEmpty(account)) {
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
                            CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(),
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
                  CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              exc);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s task failed during parentUniqueId migration for Entity Type [%s]",
                      CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
            exc);
        return;
      }

      if (toUpdateCounter == migratedCounter) {
        foundEntity.setParentIdMigrationCompleted(TRUE);
        foundEntity.setOrphanEntityParentIdMigrationCompleted(TRUE);
        log.info(format("%s job on entity [%s] for parentUniqueId Succeeded. Documents to Update: [%d], Successful: "
                + "[%d], Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
            orphanEntityCounter, skippedCounter));
      } else {
        log.warn(format("%s job failed on entity [%s] for parentUniqueId. Documents to Update: [%d], Successful: [%d], "
                + "Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
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
          CI_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), fieldName));
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
}