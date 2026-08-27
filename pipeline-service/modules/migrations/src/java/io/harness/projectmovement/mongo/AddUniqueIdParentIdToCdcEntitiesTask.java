/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.projectmovement.mongo;

import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.step.StepExecutionEntity;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.projectmovement.mongo.PipelineCDCEntitiesMigrationStatus.PipelineCDCEntitiesMigrationStatusKeys;

import com.google.inject.Inject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AddUniqueIdParentIdToCdcEntitiesTask implements Runnable {
  private static final String PIPELINE_CDC_ENTITIES_MIGRATION_LOG =
      "[PipelineAddUniqueIdAndParentUniqueIdToCdcEntitiesTask]:";
  private static final int BATCH_SIZE = 500;
  private static final String LOCK_NAME_PREFIX = "PipelineCDCEntitiesPeriodicMigrationTaskLock";

  private final MongoTemplate mongoTemplate;
  private final HPersistence persistence;
  private final PersistentLocker persistentLocker;

  // Define the entities that need CDC migration here
  private static final Map<Class<? extends PersistentEntity>, String> cdcEntitiesMap =
      Map.ofEntries(Map.entry(StepExecutionEntity.class, StepExecutionEntityKeys.lastModifiedAt));

  @Inject
  public AddUniqueIdParentIdToCdcEntitiesTask(
      MongoTemplate mongoTemplate, PersistentLocker persistentLocker, HPersistence persistence) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.persistence = persistence;
  }

  @Override
  public void run() {
    log.info(format("%s starting...", PIPELINE_CDC_ENTITIES_MIGRATION_LOG));
    for (Map.Entry<Class<? extends PersistentEntity>, String> entityMapEntry : cdcEntitiesMap.entrySet()) {
      Class<? extends PersistentEntity> clazz = entityMapEntry.getKey();
      String fieldToBeUpdated = entityMapEntry.getValue();
      PipelineCDCEntitiesMigrationStatus foundEntity = getMigrationStatus(clazz);

      if (TRUE.equals(foundEntity.getCdcMigrationCompleted())) {
        log.info(format("%s job for parentId on Entity Type: [%s] already completed.",
            PIPELINE_CDC_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
      } else {
        performCdcMigration(foundEntity, clazz, fieldToBeUpdated);
      }
    }
  }

  private void performCdcMigration(PipelineCDCEntitiesMigrationStatus migrationStatusEntity,
      final Class<? extends PersistentEntity> clazz, String fieldToBeUpdated) {
    log.info(format(
        "%s Starting uniqueId migration for Entity: [%s]", PIPELINE_CDC_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));

    int migratedCounter = 0;
    int batchSizeCounter = 0;
    int toUpdateCounter = 0;
    int skippedCounter = 0;
    int documentsWithoutAccountId = 0;
    int documentsWithoutFieldToBeUpdated = 0;

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during uniqueId migration task",
            PIPELINE_CDC_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        return;
      }
      try {
        String collectionName = mongoTemplate.getCollectionName(clazz);
        List<UpdateOneModel<Document>> bulkUpdates = new ArrayList<>();
        BulkWriteOptions options = new BulkWriteOptions().ordered(false);

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
              Object idObj = doc.get("_id");
              Object updatingField = doc.get(fieldToBeUpdated);
              String account = doc.getString(NGCommonEntityConstants.ACCOUNT_KEY);
              if (StringUtils.isEmpty(account)) {
                account = doc.getString(NGCommonEntityConstants.ACCOUNT_ID);
              }
              if (account == null) {
                // we skip this as this is stale document
                documentsWithoutAccountId++;
                continue;
              }
              if (updatingField == null) {
                // we skip this as we don't have the field to be updated
                documentsWithoutFieldToBeUpdated++;
                continue;
              }

              toUpdateCounter++;
              Long updatedCreatedAt = null;
              if (updatingField instanceof Number) {
                updatedCreatedAt = ((Number) updatingField).longValue() + 1;
              } else {
                log.debug(format("%s Unexpected createdAt type [%s], skipping document",
                    PIPELINE_CDC_ENTITIES_MIGRATION_LOG, updatingField.getClass().getName()));
                skippedCounter++;
                continue;
              }

              // Create the update operation with Bson
              BsonDocument update =
                  new BsonDocument("$set", new BsonDocument(fieldToBeUpdated, new BsonInt64(updatedCreatedAt)));

              // Dynamically create the filter based on the type of _id
              BsonDocument filter;
              if (idObj instanceof String) {
                filter = new BsonDocument("_id", new BsonString((String) idObj)); // _id is a String
              } else if (idObj instanceof ObjectId) {
                filter = new BsonDocument("_id", new BsonObjectId((ObjectId) idObj)); // _id is an ObjectId
              } else {
                // If it's neither, we'll just skip it for now (you can add error handling here)
                log.debug(format("%s Unexpected _id type [%s] for Entity: [%s], skipping document",
                    PIPELINE_CDC_ENTITIES_MIGRATION_LOG, idObj.getClass().getName(), clazz.getSimpleName()));
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
                batchSizeCounter = 0;
                Thread.sleep(2000); // Sleep for 2 seconds to avoid overwhelming the database
              }
            } catch (Exception exc) {
              log.error(format("%s job for uniqueId migration on Entity: [%s], encountered error processing document: "
                                + "[%s], skipping",
                            PIPELINE_CDC_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(),
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
                        PIPELINE_CDC_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              e);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s job for uniqueId failed on Entity Type [%s]", PIPELINE_CDC_ENTITIES_MIGRATION_LOG,
                      clazz.getSimpleName()),
            exc);
        return;
      }
    }

    if (toUpdateCounter == migratedCounter) {
      migrationStatusEntity.setCdcMigrationCompleted(TRUE);
      log.info(format("%s job on entity [%s] for uniqueId Succeeded. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          PIPELINE_CDC_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, skippedCounter));
    } else {
      log.warn(format("%s job failed on entity [%s] for uniqueId. Documents to Update: [%d], Successful: [%d], Failed: "
              + "[%d], Skipped(Failed or Invalid Entities): [%d], Skipped due to missing field: [%d], Skipped due to "
              + "missing accountId: [%d]",
          PIPELINE_CDC_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
          toUpdateCounter - migratedCounter, skippedCounter, documentsWithoutFieldToBeUpdated,
          documentsWithoutAccountId));
    }
    mongoTemplate.save(migrationStatusEntity);
  }

  private PipelineCDCEntitiesMigrationStatus getMigrationStatus(final Class<? extends PersistentEntity> clazz) {
    PipelineCDCEntitiesMigrationStatus foundEntity = persistence.createQuery(PipelineCDCEntitiesMigrationStatus.class)
                                                         .field(PipelineCDCEntitiesMigrationStatusKeys.entityClassName)
                                                         .equal(clazz.getSimpleName())
                                                         .get();

    if (foundEntity == null) {
      foundEntity = PipelineCDCEntitiesMigrationStatus.builder()
                        .entityClassName(clazz.getSimpleName())
                        .cdcMigrationCompleted(false)
                        .build();
      persistence.save(foundEntity);
    }
    return foundEntity;
  }
}