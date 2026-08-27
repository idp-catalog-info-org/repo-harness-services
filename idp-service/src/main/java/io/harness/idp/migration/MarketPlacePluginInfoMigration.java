/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
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
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class MarketPlacePluginInfoMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private PluginInfoServiceImpl pluginInfoService;

  @Override
  public void migrate() {
    log.info("Starting the migration for updating _class in plugins-info collection and status in plugin-request "
        + "collection.");

    BasicDBObject basicDBObject = new BasicDBObject();
    basicDBObject.put("_class", "io.harness.idp.configmanager.entities.MarketPlacePluginInfoEntity");
    basicDBObject.put("type", "DEFAULT");

    List<String> matchingIdentifiers = new ArrayList<>();
    DBCursor cursor = mongoPersistence.getCollection(PluginInfoEntity.class).find(basicDBObject);

    BulkWriteResult updateOperationResult;
    try {
      while (cursor.hasNext()) {
        DBObject doc = cursor.next();
        matchingIdentifiers.add((String) doc.toMap().get("identifier"));
      }

      for (String identifier : matchingIdentifiers) {
        try {
          pluginInfoService.updatePluginRequest(null, identifier, PluginRequestStatus.FULFILLED);
        } catch (Exception e) {
          log.info("No request entry found in plugin request collection for {}", identifier);
        }
      }

      BulkWriteOperation writeOperation =
          mongoPersistence.getCollection(PluginInfoEntity.class).initializeUnorderedBulkOperation();
      writeOperation.find(basicDBObject)
          .update(new BasicDBObject(
              "$set", new BasicDBObject("_class", "io.harness.idp.configmanager.entities.DefaultPluginInfoEntity")));
      updateOperationResult = writeOperation.execute();
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Updated _class and status field successfully for {} records", updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update _class and status field for any record");
    }
    log.info("Migration complete for updating _class field in plugins-info collection and status in plugin-request"
        + " collection.");
  }
}
