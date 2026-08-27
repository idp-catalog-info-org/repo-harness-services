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
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PluginsInfoTypeClassMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  private static final Map<String, String> typeClassMap =
      Map.of("DEFAULT", "io.harness.idp.configmanager.entities.DefaultPluginInfoEntity", "CUSTOM",
          "io.harness.idp.configmanager.entities.CustomPluginInfoEntity");

  @Override
  public void migrate() {
    log.info("Starting the migration for updating _class field in plugins-info collection.");

    for (Map.Entry<String, String> entry : typeClassMap.entrySet()) {
      String type = entry.getKey();
      String className = entry.getValue();
      BasicDBObject basicDBObject = new BasicDBObject("type", type);
      BulkWriteOperation writeOperation =
          mongoPersistence.getCollection(PluginInfoEntity.class).initializeUnorderedBulkOperation();
      writeOperation.find(basicDBObject).update(new BasicDBObject("$set", new BasicDBObject("_class", className)));
      BulkWriteResult updateOperationResult = writeOperation.execute();
      if (updateOperationResult.getModifiedCount() > 0) {
        log.info("Updated _class field for {} type successfully for {} records", type,
            updateOperationResult.getModifiedCount());
      } else {
        log.warn("Could not update _class field for {} type for any record", type);
      }
    }
    log.info("Migration complete for updating _class field in plugins-info collection.");
  }
}
