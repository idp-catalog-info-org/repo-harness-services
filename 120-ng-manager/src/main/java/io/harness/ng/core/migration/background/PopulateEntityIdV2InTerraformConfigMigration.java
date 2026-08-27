/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.services.ScopeInfoService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class PopulateEntityIdV2InTerraformConfigMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private ScopeInfoService scopeInfoService;

  private static final String DEBUG_LOG = "[PopulateEntityIdV2InTerraformConfigMigration]: ";
  private static final String COLLECTION = "terraformConfig";
  private static final String ORPHAN_ENTITY_PREFIX = "orphan_";
  private static final String LOCAL_MAP_DELIMITER = "|";
  private static final int BATCH_SIZE = 500;

  private final Map<String, String> scopeInfoCache = new HashMap<>();

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Starting migration to populate entityIdV2 in TerraformConfig");

    int migratedCounter = 0;
    int orphanCounter = 0;
    int batchCounter = 0;

    // Build query: entityIdV2 is null or doesn't exist
    Criteria criteria =
        new Criteria().orOperator(Criteria.where("entityIdV2").exists(false), Criteria.where("entityIdV2").is(null));
    Query query = new Query(criteria).limit(NO_LIMIT).maxTimeMsec(MAX_VALUE).cursorBatchSize(BATCH_SIZE);

    BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, COLLECTION);

    try (Stream<Document> stream = mongoTemplate.stream(query, Document.class, COLLECTION)) {
      Iterator<Document> iterator = stream.iterator();
      while (iterator.hasNext()) {
        Document doc = iterator.next();
        Object docIdObj = doc.get("_id");
        String docIdStr = (docIdObj instanceof ObjectId) ? ((ObjectId) docIdObj).toHexString() : docIdObj.toString();

        String entityIdV2;
        try {
          entityIdV2 = processDocument(doc);

          // If we can't compute entityIdV2, use orphan prefix to ensure document gets a value
          if (isEmpty(entityIdV2)) {
            entityIdV2 = ORPHAN_ENTITY_PREFIX + generateUuid();
            orphanCounter++;
          }
        } catch (Exception e) {
          // On any exception, mark as orphan to ensure document gets a value
          log.warn(format(
              "%s Failed to process document _id=%s, marking as orphan: %s", DEBUG_LOG, docIdStr, e.getMessage()));
          entityIdV2 = ORPHAN_ENTITY_PREFIX + generateUuid();
          orphanCounter++;
        }

        // Add update to bulk batch
        Update updateOp = new Update().set("entityIdV2", entityIdV2);
        bulkOperations.updateOne(new Query(Criteria.where("_id").is(docIdObj)), updateOp);
        batchCounter++;
        migratedCounter++;

        // Execute bulk when batch is full
        if (batchCounter >= BATCH_SIZE) {
          bulkOperations.execute();
          bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, COLLECTION);
          batchCounter = 0;
          log.info(format("%s Progress: migrated: [%d], orphan: [%d]", DEBUG_LOG, migratedCounter, orphanCounter));
        }
      }

      // Execute remaining updates
      if (batchCounter > 0) {
        bulkOperations.execute();
      }
    }

    log.info(format("%s Migration completed. Migrated: [%d], Orphan: [%d]", DEBUG_LOG, migratedCounter, orphanCounter));
  }

  private String processDocument(Document doc) {
    String entityId = doc.getString("entityId");
    Object docIdObj = doc.get("_id");
    String docId = (docIdObj instanceof ObjectId) ? ((ObjectId) docIdObj).toHexString() : docIdObj.toString();

    if (isEmpty(entityId)) {
      log.debug(DEBUG_LOG + "EntityId is null or empty for TerraformConfig _id: {}", docId);
      return null;
    }

    String[] ids = entityId.split("/", 4);
    if (ids.length != 4) {
      log.debug(
          DEBUG_LOG + "EntityId does not have 4 parts (skipping): {} for TerraformConfig _id: {}", entityId, docId);
      return null;
    }

    String accountId = ids[0];
    String orgId = ids[1];
    String projectId = ids[2];
    String provisionerId = ids[3];

    String parentUniqueId = fetchScopeUniqueId(accountId, orgId, projectId, docId);
    if (isEmpty(parentUniqueId)) {
      return null;
    }

    return parentUniqueId + "/" + provisionerId;
  }

  private String fetchScopeUniqueId(String accountId, String orgId, String projectId, String documentId) {
    String key = accountId + LOCAL_MAP_DELIMITER + orgId + LOCAL_MAP_DELIMITER + projectId;

    if (scopeInfoCache.containsKey(key)) {
      return scopeInfoCache.get(key);
    }

    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountId, orgId, projectId);

      if (scopeInfo == null || scopeInfo.getUniqueId() == null) {
        log.error(DEBUG_LOG + "ScopeInfo null for account={}, org={}, project={}, documentId={}", accountId, orgId,
            projectId, documentId);
        return null;
      }

      scopeInfoCache.put(key, scopeInfo.getUniqueId());
      return scopeInfo.getUniqueId();

    } catch (Exception e) {
      log.error(format("%s Failed fetching scopeInfo for documentId=%s (%s/%s/%s)", DEBUG_LOG, documentId, accountId,
                    orgId, projectId),
          e);
      return null;
    }
  }
}
