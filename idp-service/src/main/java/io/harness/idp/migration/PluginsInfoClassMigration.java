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
public class PluginsInfoClassMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info("Starting the migration for updating _class field in plugins-info collection.");

    BasicDBObject basicDBObject = new BasicDBObject("type", new BasicDBObject("$exists", false));
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(PluginInfoEntity.class).initializeUnorderedBulkOperation();
    writeOperation.find(basicDBObject)
        .update(new BasicDBObject(
            "$set", new BasicDBObject("_class", "io.harness.idp.configmanager.entities.DefaultPluginInfoEntity")));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Updated _class field successfully for {} records", updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update _class field for any record");
    }
    log.info("Migration complete for updating _class field in plugins-info collection.");
  }
}
