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
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PluginsInfoIsDeletedMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding isDeleted field in plugins-info collection.");

    BasicDBObject basicDBObject = new BasicDBObject("type", "DEFAULT");
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(PluginInfoEntity.class).initializeUnorderedBulkOperation();
    writeOperation.find(basicDBObject).update(new BasicDBObject("$set", new BasicDBObject("isDeleted", false)));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Added isDeleted field successfully from {} records", updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not add isDeleted field to any record");
    }

    log.info("Migration complete for adding isDeleted field in plugins-info collection.");
  }
}
