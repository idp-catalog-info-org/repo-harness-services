/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.idp.configmanager.service.PluginInfoServiceImpl;
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.spec.server.idp.v1.model.PluginRequestStatus;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PluginRequestMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private PluginInfoServiceImpl pluginInfoService;

  @Override
  public void migrate() {
    log.info("Starting the migration for updating status and identifier field in request-plugin collection.");

    DBCursor cursor = mongoPersistence.getCollection(PluginRequestEntity.class).find(new BasicDBObject());
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(PluginRequestEntity.class).initializeUnorderedBulkOperation();
    BulkWriteResult updateOperationResult;

    try {
      while (cursor.hasNext()) {
        DBObject doc = cursor.next();
        String name = doc.toMap().get("name").toString().toLowerCase();

        BasicDBObject updateFields = new BasicDBObject();
        updateFields.put("identifier", name);
        updateFields.put("status", PluginRequestStatus.IN_PROGRESS);

        writeOperation.find(new BasicDBObject("_id", doc.toMap().get("_id")))
            .update(new BasicDBObject("$set", updateFields));
      }

      updateOperationResult = writeOperation.execute();
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info(
          "Updated status and identifier field successfully for {} records", updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update status and identifier field for any record");
    }
    log.info("Migration complete for updating status and identifier field in plugin-request collection.");
  }
}
